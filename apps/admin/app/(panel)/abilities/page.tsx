"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  addEdge,
  Background,
  Controls,
  Handle,
  MiniMap,
  Position,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  type Connection,
  type Edge,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { toast } from "sonner";
import {
  createTechnique,
  deployTechniques,
  getTechnique,
  listTechniques,
  previewTechnique,
  updateTechnique,
  validateTechnique,
  type EffectSpec,
  type GraphNode,
  type GraphStatus,
  type Requirement,
  type RequirementType,
  type TechniqueGraphDoc,
  type TechniqueSummary,
} from "../../../lib/abilities";

/* ───────── spell-class catalogue (les plus utilisés sur le serveur) ───────── */

const SPELL_CLASSES: { group: string; items: { cls: string; label: string }[] }[] = [
  {
    group: "Structure",
    items: [
      { cls: ".MultiSpell", label: "MultiSpell (chaîne)" },
      { cls: ".TargetedMultiSpell", label: "TargetedMultiSpell" },
      { cls: ".instant.DummySpell", label: "DummySpell (effets purs)" },
      { cls: ".ExternalCommandSpell", label: "ExternalCommand (→ cmd/emote)" },
      { cls: ".PassiveSpell", label: "PassiveSpell (trigger)" },
    ],
  },
  {
    group: "Projectiles / mouvement",
    items: [
      { cls: ".instant.ParticleProjectileSpell", label: "ParticleProjectile" },
      { cls: ".instant.LeapSpell", label: "Leap (saut)" },
      { cls: ".instant.VelocitySpell", label: "Velocity (propulsion)" },
      { cls: ".instant.ThrowBlockSpell", label: "ThrowBlock" },
      { cls: ".targeted.ForcetossSpell", label: "Forcetoss" },
    ],
  },
  {
    group: "Zone / cible",
    items: [
      { cls: ".targeted.AreaEffectSpell", label: "AreaEffect (zone/cône)" },
      { cls: ".targeted.NovaSpell", label: "Nova (anneau)" },
      { cls: ".targeted.PulserSpell", label: "Pulser (au sol)" },
      { cls: ".targeted.OrbitSpell", label: "Orbit" },
      { cls: ".targeted.PainSpell", label: "Pain (dégâts)" },
      { cls: ".targeted.PotionEffectSpell", label: "PotionEffect (statut)" },
      { cls: ".targeted.StunSpell", label: "Stun" },
    ],
  },
  {
    group: "Divers / buff",
    items: [
      { cls: ".instant.ConjureSpell", label: "Conjure (donne item)" },
      { cls: ".buff.InvulnerabilitySpell", label: "Invulnerability" },
      { cls: ".buff.WindwalkSpell", label: "Windwalk (vol)" },
    ],
  },
];
const ALL_CLASSES = SPELL_CLASSES.flatMap((g) => g.items);

const REQUIREMENT_TYPES: RequirementType[] = [
  "ability", "mastery", "rank", "skill", "level",
  "clan", "village", "affinity", "nature", "exam", "mentor",
];

const EFFECT_POSITIONS = ["caster", "target", "line", "trail", "delayed", "buff", "orbit", "special", "projectile", "start"];
const EFFECT_TYPES = ["particles", "sound", "entity", "effectlib"];

/* ───────── RF <-> doc mapping ───────── */

const RF_TYPE = "reborn";

function docToRf(doc: TechniqueGraphDoc): { nodes: Node[]; edges: Edge[] } {
  const nodes: Node[] = doc.nodes.map((n, i) => ({
    id: n.id,
    type: RF_TYPE,
    position: n.position ?? { x: 60 + (i % 4) * 240, y: 60 + Math.floor(i / 4) * 170 },
    data: { node: n },
  }));
  const edges: Edge[] = doc.edges.map((e, i) => ({
    id: `${e.from}__${e.to}__${i}`,
    source: e.from,
    target: e.to,
    label: e.role === "root" ? "racine" : e.delay ? `DELAY ${e.delay}` : "",
    animated: e.role === "root",
    data: { role: e.role, delay: e.delay, mode: e.mode, order: e.order },
  }));
  return { nodes, edges };
}

function rfToDoc(base: TechniqueGraphDoc, nodes: Node[], edges: Edge[]): TechniqueGraphDoc {
  return {
    ...base,
    nodes: nodes.map((rf) => {
      const n = (rf.data as { node: GraphNode }).node;
      return { ...n, id: rf.id, position: rf.position };
    }),
    edges: edges.map((e) => {
      const d = (e.data ?? {}) as { role?: string; delay?: number; mode?: string; order?: number };
      return {
        from: e.source, to: e.target,
        role: (d.role as "root" | "chain") ?? "chain",
        delay: d.delay, mode: d.mode as never, order: d.order,
      };
    }),
  };
}

/* ───────── custom node ───────── */

function RebornNode({ data, selected }: NodeProps) {
  const n = (data as { node: GraphNode }).node;
  const color = n.type === "technique" ? "#3b5bdb" : n.type === "magicItem" ? "#0891b2" : "#dc2626";
  const title = n.type === "technique" ? "Technique" : n.type === "magicItem" ? "Magic-item" : "Sort";
  const sub =
    n.type === "technique" ? `${n.method} · ${n.chakra ?? 0} chakra`
    : n.type === "magicItem" ? n.itemId
    : (ALL_CLASSES.find((c) => c.cls === n.spellClass)?.label ?? n.spellClass ?? "?");
  return (
    <div
      style={{ borderColor: selected ? color : "var(--color-border-strong)", boxShadow: selected ? `0 0 0 2px ${color}55` : "none" }}
      className="min-w-[160px] rounded-md border bg-[var(--color-surface-elevated)] text-[var(--color-foreground)]"
    >
      {n.type !== "technique" && <Handle type="target" position={Position.Left} style={{ background: color }} />}
      <div className="rounded-t-md px-2 py-1 text-[11px] font-semibold uppercase tracking-wide" style={{ background: color, color: "#fff" }}>
        {title}
      </div>
      <div className="px-2 py-1">
        <div className="text-[12px] font-medium truncate">{n.type === "technique" ? "▶ cast" : n.id}</div>
        <div className="text-[11px] text-[var(--color-foreground-muted)] truncate">{sub}</div>
        {n.type === "spell" && (n.effects?.length ?? 0) > 0 && (
          <div className="text-[10px] text-[var(--color-foreground-subtle)]">{n.effects!.length} effet(s)</div>
        )}
      </div>
      {n.type !== "magicItem" && <Handle type="source" position={Position.Right} style={{ background: color }} />}
    </div>
  );
}
const nodeTypes = { [RF_TYPE]: RebornNode };

/* ───────── helpers ───────── */

let counter = 0;
function freshId(prefix: string): string {
  counter += 1;
  return `${prefix}_${Date.now().toString(36)}_${counter}`;
}

function emptyDoc(slug: string): TechniqueGraphDoc {
  const rootId = "ms_" + slug;
  return {
    id: slug, name: slug, category: "ninjutsu/pur", rank: "D", execution: "JUTSU", description: "",
    nodes: [
      { id: "frame", type: "technique", method: "LEFT_CLICK", itemType: "ROULEAU", chakra: 40, cooldownMs: 5000, requires: [], position: { x: 40, y: 200 } },
      { id: rootId, type: "spell", spellClass: ".MultiSpell", helperSpell: false, options: {}, effects: [], modifiers: [], position: { x: 340, y: 200 } },
    ],
    edges: [{ from: "frame", to: rootId, role: "root" }],
  };
}

/* ───────── editor ───────── */

function EditorInner() {
  const [list, setList] = useState<TechniqueSummary[]>([]);
  const [current, setCurrent] = useState<{ dbId: string; status: GraphStatus; doc: TechniqueGraphDoc } | null>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const docRef = useRef<TechniqueGraphDoc | null>(null);

  const refreshList = useCallback(async () => {
    try { setList(await listTechniques()); }
    catch (e) { toast.error(`Chargement impossible : ${(e as Error).message}`); }
  }, []);
  useEffect(() => { void refreshList(); }, [refreshList]);

  const loadDoc = useCallback((dbId: string, status: GraphStatus, doc: TechniqueGraphDoc) => {
    docRef.current = doc;
    setCurrent({ dbId, status, doc });
    const { nodes: n, edges: e } = docToRf(doc);
    setNodes(n); setEdges(e); setSelectedNodeId(null); setSelectedEdgeId(null);
  }, [setNodes, setEdges]);

  const openTechnique = useCallback(async (id: string) => {
    try { const full = await getTechnique(id); loadDoc(full.id, full.status, full.graph); }
    catch (e) { toast.error(`Ouverture impossible : ${(e as Error).message}`); }
  }, [loadDoc]);

  const onConnect = useCallback((c: Connection) => {
    const srcNode = nodes.find((n) => n.id === c.source);
    const srcType = srcNode ? (srcNode.data as { node: GraphNode }).node.type : "spell";
    const role = srcType === "technique" ? "root" : "chain";
    setEdges((eds) => {
      // au plus une arête root
      const cleaned = role === "root" ? eds.filter((e) => (e.data as { role?: string })?.role !== "root") : eds;
      return addEdge(
        { ...c, id: `${c.source}__${c.target}__${Date.now().toString(36)}`, label: role === "root" ? "racine" : "", animated: role === "root", data: { role } },
        cleaned,
      );
    });
  }, [nodes, setEdges]);

  const addSpell = useCallback((cls: string) => {
    const id = freshId("spell");
    const node: GraphNode = { id, type: "spell", spellClass: cls, helperSpell: true, options: {}, effects: [], modifiers: [], position: { x: 380, y: 80 + nodes.length * 40 } };
    setNodes((nds) => [...nds, { id, type: RF_TYPE, position: node.position!, data: { node } }]);
    setSelectedNodeId(id); setSelectedEdgeId(null);
  }, [nodes.length, setNodes]);

  const addMagicItem = useCallback(() => {
    const id = freshId("item");
    const node: GraphNode = { id, type: "magicItem", itemId: id, options: { type: "paper" }, position: { x: 380, y: 400 } };
    setNodes((nds) => [...nds, { id, type: RF_TYPE, position: node.position!, data: { node } }]);
    setSelectedNodeId(id); setSelectedEdgeId(null);
  }, [setNodes]);

  const patchNode = useCallback((patch: Partial<GraphNode>) => {
    setNodes((nds) => nds.map((rf) => {
      if (rf.id !== selectedNodeId) return rf;
      const n = (rf.data as { node: GraphNode }).node;
      return { ...rf, data: { node: { ...n, ...patch } } };
    }));
  }, [selectedNodeId, setNodes]);

  const patchEdge = useCallback((patch: Record<string, unknown>) => {
    setEdges((eds) => eds.map((e) => {
      if (e.id !== selectedEdgeId) return e;
      const data = { ...(e.data ?? {}), ...patch };
      return { ...e, data, label: data.role === "root" ? "racine" : data.delay ? `DELAY ${data.delay}` : "" };
    }));
  }, [selectedEdgeId, setEdges]);

  const deleteSelectedNode = useCallback(() => {
    if (!selectedNodeId) return;
    const n = nodes.find((x) => x.id === selectedNodeId);
    if (n && (n.data as { node: GraphNode }).node.type === "technique") {
      toast.error("Le nœud technique ne peut pas être supprimé."); return;
    }
    setNodes((nds) => nds.filter((x) => x.id !== selectedNodeId));
    setEdges((eds) => eds.filter((e) => e.source !== selectedNodeId && e.target !== selectedNodeId));
    setSelectedNodeId(null);
  }, [selectedNodeId, nodes, setNodes, setEdges]);

  const selectedNode = useMemo(() => {
    const rf = nodes.find((n) => n.id === selectedNodeId);
    return rf ? (rf.data as { node: GraphNode }).node : null;
  }, [nodes, selectedNodeId]);
  const selectedEdge = useMemo(() => edges.find((e) => e.id === selectedEdgeId) ?? null, [edges, selectedEdgeId]);

  const buildDoc = useCallback((): TechniqueGraphDoc | null => {
    if (!current || !docRef.current) return null;
    return rfToDoc(docRef.current, nodes, edges);
  }, [current, nodes, edges]);

  const save = useCallback(async () => {
    if (!current) return;
    const doc = buildDoc(); if (!doc) return;
    setBusy(true);
    try {
      await updateTechnique(current.dbId, { name: doc.name, category: doc.category, status: current.status, graph: doc });
      docRef.current = doc;
      toast.success("Technique enregistrée."); void refreshList();
    } catch (e) { toast.error(`Échec de l'enregistrement : ${(e as Error).message}`); }
    finally { setBusy(false); }
  }, [current, buildDoc, refreshList]);

  const validate = useCallback(async () => {
    if (!current) return; await save(); setBusy(true);
    try {
      const r = await validateTechnique(current.dbId);
      if (r.warnings.length) toast.warning(`Valide, ${r.warnings.length} avertissement(s)`, { description: r.warnings.slice(0, 4).join(" · ") });
      else toast.success("Graphe valide (schéma + DAG + réfs + particules).");
    } catch (e) { toast.error(`Invalide : ${(e as Error).message}`); }
    finally { setBusy(false); }
  }, [current, save]);

  const preview = useCallback(async () => {
    if (!current) return; await save(); setBusy(true);
    try { const r = await previewTechnique(current.dbId); toast.success("Aperçu envoyé en jeu", { description: r.queued }); }
    catch (e) { toast.error(`Aperçu impossible : ${(e as Error).message}`); }
    finally { setBusy(false); }
  }, [current, save]);

  const deploy = useCallback(async () => {
    setBusy(true);
    try {
      const r = await deployTechniques();
      toast.success(`Déployé : ${r.deployed} technique(s), ${r.spells} sort(s) MS, ${r.magicItems} item(s)`, { description: `reload : ${r.reloaded.join(", ")}` });
    } catch (e) { toast.error(`Déploiement échoué : ${(e as Error).message}`); }
    finally { setBusy(false); }
  }, []);

  const newTechnique = useCallback(async () => {
    const slug = window.prompt("Identifiant (lower_snake_case) :")?.trim();
    if (!slug) return;
    if (!/^[a-z0-9_]+$/.test(slug)) { toast.error("Identifiant invalide (a-z, 0-9, _)."); return; }
    setBusy(true);
    try {
      const doc = emptyDoc(slug);
      const created = await createTechnique({ slug, name: slug, category: doc.category, graph: doc });
      await refreshList(); loadDoc(created.id, created.status, doc);
      toast.success(`Technique '${slug}' créée.`);
    } catch (e) { toast.error(`Création impossible : ${(e as Error).message}`); }
    finally { setBusy(false); }
  }, [refreshList, loadDoc]);

  const setStatus = useCallback((status: GraphStatus) => setCurrent((c) => (c ? { ...c, status } : c)), []);

  return (
    <div className="flex h-[calc(100vh-0px)] w-full">
      {/* liste */}
      <aside className="w-56 shrink-0 border-r border-[var(--color-border)] bg-[var(--color-surface)] p-3 overflow-y-auto">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold">Techniques</h2>
          <button onClick={newTechnique} className="rounded bg-[var(--color-accent)] px-2 py-1 text-xs text-white hover:opacity-90">+ Nouvelle</button>
        </div>
        <ul className="space-y-1">
          {list.map((t) => (
            <li key={t.id}>
              <button onClick={() => openTechnique(t.id)}
                className={`w-full rounded px-2 py-1.5 text-left text-xs hover:bg-[var(--color-surface-elevated)] ${current?.dbId === t.id ? "bg-[var(--color-surface-elevated)]" : ""}`}>
                <div className="font-medium truncate">{t.name}</div>
                <div className="text-[10px] text-[var(--color-foreground-muted)]">{t.slug} · {t.status}</div>
              </button>
            </li>
          ))}
          {list.length === 0 && <li className="text-xs text-[var(--color-foreground-muted)]">Aucune technique.</li>}
        </ul>
      </aside>

      {/* canvas */}
      <main className="relative flex-1 min-w-0">
        {current ? (
          <>
            <div className="absolute left-0 right-0 top-0 z-10 flex flex-wrap items-center gap-2 border-b border-[var(--color-border)] bg-[var(--color-surface)]/90 px-3 py-2 backdrop-blur">
              <span className="text-sm font-semibold">{current.doc.id}</span>
              <select value={current.status} onChange={(e) => setStatus(e.target.value as GraphStatus)}
                className="rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-2 py-1 text-xs">
                <option value="DRAFT">DRAFT</option><option value="PUBLISHED">PUBLISHED</option><option value="ARCHIVED">ARCHIVED</option>
              </select>
              <div className="mx-1 h-4 w-px bg-[var(--color-border)]" />
              <AddSpellMenu onAdd={addSpell} />
              <button onClick={addMagicItem} className="rounded px-2 py-1 text-[11px] text-white hover:opacity-90" style={{ background: "#0891b2" }}>+ Magic-item</button>
              <div className="ml-auto flex gap-2">
                <button disabled={busy} onClick={save} className="rounded border border-[var(--color-border)] px-3 py-1 text-xs hover:bg-[var(--color-surface-elevated)] disabled:opacity-50">Enregistrer</button>
                <button disabled={busy} onClick={validate} className="rounded border border-[var(--color-border)] px-3 py-1 text-xs hover:bg-[var(--color-surface-elevated)] disabled:opacity-50">Valider</button>
                <button disabled={busy} onClick={preview} className="rounded border border-[var(--color-border)] px-3 py-1 text-xs hover:bg-[var(--color-surface-elevated)] disabled:opacity-50" title="Joue l'effet sur ton perso en jeu">Tester sur moi</button>
                <button disabled={busy} onClick={deploy} className="rounded bg-[var(--color-accent)] px-3 py-1 text-xs text-white hover:opacity-90 disabled:opacity-50">Déployer</button>
              </div>
            </div>
            <ReactFlow
              nodes={nodes} edges={edges}
              onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} onConnect={onConnect}
              onNodeClick={(_, n) => { setSelectedNodeId(n.id); setSelectedEdgeId(null); }}
              onEdgeClick={(_, e) => { setSelectedEdgeId(e.id); setSelectedNodeId(null); }}
              onPaneClick={() => { setSelectedNodeId(null); setSelectedEdgeId(null); }}
              nodeTypes={nodeTypes} fitView proOptions={{ hideAttribution: true }}
              className="bg-[var(--color-background)]"
            >
              <Background color="#1f2430" gap={20} />
              <Controls />
              <MiniMap pannable zoomable className="!bg-[var(--color-surface)]" />
            </ReactFlow>
          </>
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-[var(--color-foreground-muted)]">Sélectionne ou crée une technique.</div>
        )}
      </main>

      {/* inspecteur */}
      {(selectedNode || selectedEdge) && (
        <aside className="w-80 shrink-0 border-l border-[var(--color-border)] bg-[var(--color-surface)] p-3 overflow-y-auto text-xs">
          {selectedNode && (
            <>
              <div className="mb-2 flex items-center justify-between">
                <h3 className="text-sm font-semibold capitalize">{selectedNode.type}</h3>
                {selectedNode.type !== "technique" && <button onClick={deleteSelectedNode} className="rounded px-2 py-1 text-[var(--color-danger)] hover:bg-[var(--color-surface-elevated)]">Supprimer</button>}
              </div>
              {selectedNode.type === "technique" && <TechniqueInspector node={selectedNode} onPatch={patchNode} />}
              {selectedNode.type === "spell" && <SpellInspector node={selectedNode} onPatch={patchNode} />}
              {selectedNode.type === "magicItem" && <MagicItemInspector node={selectedNode} onPatch={patchNode} />}
            </>
          )}
          {selectedEdge && !selectedNode && <EdgeInspector edge={selectedEdge} onPatch={patchEdge} />}
        </aside>
      )}
    </div>
  );
}

/* ───────── palette ───────── */

function AddSpellMenu({ onAdd }: { onAdd: (cls: string) => void }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative">
      <button onClick={() => setOpen((o) => !o)} className="rounded px-2 py-1 text-[11px] text-white hover:opacity-90" style={{ background: "#dc2626" }}>+ Sort ▾</button>
      {open && (
        <div className="absolute z-20 mt-1 max-h-80 w-60 overflow-y-auto rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] p-1 shadow-lg">
          {SPELL_CLASSES.map((g) => (
            <div key={g.group}>
              <div className="px-2 py-1 text-[10px] uppercase text-[var(--color-foreground-muted)]">{g.group}</div>
              {g.items.map((it) => (
                <button key={it.cls} onClick={() => { onAdd(it.cls); setOpen(false); }}
                  className="block w-full rounded px-2 py-1 text-left text-[11px] hover:bg-[var(--color-surface)]">{it.label}</button>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ───────── inspectors ───────── */

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block"><span className="mb-1 block text-[var(--color-foreground-subtle)]">{label}</span>{children}</label>;
}
const inputCls = "w-full rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-2 py-1";

function TechniqueInspector({ node, onPatch }: { node: GraphNode; onPatch: (p: Partial<GraphNode>) => void }) {
  return (
    <div className="space-y-3">
      <p className="text-[11px] text-[var(--color-foreground-muted)]">Le cadre ShinobiCore : entrée, coût, recharge, prérequis. Relie-le (poignée droite) au sort racine.</p>
      <Field label="Entrée"><select value={node.method ?? "LEFT_CLICK"} onChange={(e) => onPatch({ method: e.target.value as GraphNode["method"] })} className={inputCls}>
        {["LEFT_CLICK", "RIGHT_CLICK", "HOLD_SNEAK", "CLICK_SEQUENCE"].map((m) => <option key={m}>{m}</option>)}</select></Field>
      <Field label="JutsuItem (canal)"><input className={inputCls} value={node.itemType ?? ""} onChange={(e) => onPatch({ itemType: e.target.value })} /></Field>
      <Field label="Chakra"><input type="number" className={inputCls} value={node.chakra ?? 0} onChange={(e) => onPatch({ chakra: Number(e.target.value) })} /></Field>
      <Field label="Recharge (ms)"><input type="number" className={inputCls} value={node.cooldownMs ?? 0} onChange={(e) => onPatch({ cooldownMs: Number(e.target.value) })} /></Field>
      <Field label="Maîtrise / cast"><input type="number" className={inputCls} value={node.masteryPerCast ?? 0} onChange={(e) => onPatch({ masteryPerCast: e.target.value === "" ? undefined : Number(e.target.value) })} /></Field>
      <RequirementsEditor value={node.requires ?? []} onChange={(r) => onPatch({ requires: r })} />
    </div>
  );
}

function SpellInspector({ node, onPatch }: { node: GraphNode; onPatch: (p: Partial<GraphNode>) => void }) {
  return (
    <div className="space-y-3">
      <Field label="Classe de sort (spell-class)">
        <input list="spellclasses" className={inputCls} value={node.spellClass ?? ""} onChange={(e) => onPatch({ spellClass: e.target.value })} />
        <datalist id="spellclasses">{ALL_CLASSES.map((c) => <option key={c.cls} value={c.cls}>{c.label}</option>)}</datalist>
      </Field>
      <Field label="Nom affiché (name)"><input className={inputCls} value={node.displayName ?? ""} onChange={(e) => onPatch({ displayName: e.target.value })} placeholder="&cKaton &r&f- ..." /></Field>
      <label className="flex items-center gap-2"><input type="checkbox" checked={node.helperSpell ?? true} onChange={(e) => onPatch({ helperSpell: e.target.checked })} /><span>helper-spell (sous-sort caché)</span></label>
      <KVEditor title="Options (n'importe quelle clé MagicSpells)" value={node.options ?? {}} onChange={(o) => onPatch({ options: o })} placeholder="ex. damage / horizontal-radius / projectile-velocity" />
      <EffectsEditor value={node.effects ?? []} onChange={(fx) => onPatch({ effects: fx })} />
      <StringListEditor title="Modifiers" value={node.modifiers ?? []} onChange={(m) => onPatch({ modifiers: m })} placeholder="ex. variable coup=1 required" />
    </div>
  );
}

function MagicItemInspector({ node, onPatch }: { node: GraphNode; onPatch: (p: Partial<GraphNode>) => void }) {
  return (
    <div className="space-y-3">
      <Field label="Id de l'item (référencé par item:/cast-item:)"><input className={inputCls} value={node.itemId ?? ""} onChange={(e) => onPatch({ itemId: e.target.value })} /></Field>
      <KVEditor title="Propriétés" value={node.options ?? {}} onChange={(o) => onPatch({ options: o })} placeholder="type / item-model / custom-model-data / name" />
      <p className="text-[10px] text-[var(--color-foreground-muted)]">Nexo : mets <code>item-model: nexo:mon_modele</code>.</p>
    </div>
  );
}

function EdgeInspector({ edge, onPatch }: { edge: Edge; onPatch: (p: Record<string, unknown>) => void }) {
  const d = (edge.data ?? {}) as { role?: string; delay?: number; mode?: string; order?: number };
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold">Liaison</h3>
      <p className="text-[11px] text-[var(--color-foreground-muted)]">{d.role === "root" ? "Cadre → sort racine (cast au déclenchement)." : "Sort → sous-sort. Pour un MultiSpell, l'ordre et le délai composent la chaîne."}</p>
      {d.role !== "root" && <>
        <Field label="Délai avant ce sous-sort (ticks)"><input type="number" className={inputCls} value={d.delay ?? ""} onChange={(e) => onPatch({ delay: e.target.value === "" ? undefined : Number(e.target.value) })} /></Field>
        <Field label="Ordre dans la chaîne"><input type="number" className={inputCls} value={d.order ?? ""} onChange={(e) => onPatch({ order: e.target.value === "" ? undefined : Number(e.target.value) })} /></Field>
        <Field label="Mode de cast"><select value={d.mode ?? ""} onChange={(e) => onPatch({ mode: e.target.value || undefined })} className={inputCls}>
          <option value="">(défaut)</option>{["full", "partial", "hard", "direct", "none"].map((m) => <option key={m}>{m}</option>)}</select></Field>
      </>}
    </div>
  );
}

/* ───────── sub-editors ───────── */

function RequirementsEditor({ value, onChange }: { value: Requirement[]; onChange: (r: Requirement[]) => void }) {
  const set = (i: number, patch: Partial<Requirement>) => onChange(value.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between"><span className="text-[var(--color-foreground-subtle)]">Prérequis</span>
        <button onClick={() => onChange([...value, { type: "ability", id: "" }])} className="rounded bg-[var(--color-accent)] px-2 py-0.5 text-[11px] text-white">+ Ajouter</button></div>
      {value.map((r, i) => (
        <div key={i} className="rounded border border-[var(--color-border)] p-2 space-y-1">
          <div className="flex gap-1">
            <select value={r.type} onChange={(e) => set(i, { type: e.target.value as RequirementType })} className="flex-1 rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-1 py-0.5">
              {REQUIREMENT_TYPES.map((t) => <option key={t}>{t}</option>)}</select>
            <button onClick={() => onChange(value.filter((_, idx) => idx !== i))} className="rounded px-1.5 text-[var(--color-danger)] hover:bg-[var(--color-surface-elevated)]">×</button>
          </div>
          <input placeholder="id (controle_chakra, katon, chunin…)" value={r.id ?? ""} onChange={(e) => set(i, { id: e.target.value })} className={inputCls} />
          {(r.type === "mastery" || r.type === "skill" || r.type === "level") && (
            <input type="number" placeholder="min" value={r.min ?? ""} onChange={(e) => set(i, { min: e.target.value === "" ? undefined : Number(e.target.value) })} className={inputCls} />
          )}
        </div>
      ))}
    </div>
  );
}

function StringListEditor({ title, value, onChange, placeholder }: { title: string; value: string[]; onChange: (v: string[]) => void; placeholder?: string }) {
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between"><span className="text-[var(--color-foreground-subtle)]">{title}</span>
        <button onClick={() => onChange([...value, ""])} className="rounded bg-[var(--color-accent)] px-2 py-0.5 text-[11px] text-white">+</button></div>
      {value.map((s, i) => (
        <div key={i} className="flex gap-1">
          <input className={inputCls} value={s} placeholder={placeholder} onChange={(e) => onChange(value.map((x, idx) => (idx === i ? e.target.value : x)))} />
          <button onClick={() => onChange(value.filter((_, idx) => idx !== i))} className="rounded px-1.5 text-[var(--color-danger)] hover:bg-[var(--color-surface-elevated)]">×</button>
        </div>
      ))}
    </div>
  );
}

/** Generic key/value editor → any MagicSpells option (string/number/bool auto-detected). */
function KVEditor({ title, value, onChange, placeholder }: { title: string; value: Record<string, unknown>; onChange: (v: Record<string, unknown>) => void; placeholder?: string }) {
  const entries = Object.entries(value);
  const setKey = (oldK: string, newK: string) => {
    const o: Record<string, unknown> = {};
    for (const [k, v] of entries) o[k === oldK ? newK : k] = v;
    onChange(o);
  };
  const setVal = (k: string, raw: string) => onChange({ ...value, [k]: coerce(raw) });
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between"><span className="text-[var(--color-foreground-subtle)]">{title}</span>
        <button onClick={() => onChange({ ...value, "": "" })} className="rounded bg-[var(--color-accent)] px-2 py-0.5 text-[11px] text-white">+</button></div>
      {placeholder && entries.length === 0 && <p className="text-[10px] text-[var(--color-foreground-muted)]">{placeholder}</p>}
      {entries.map(([k, v], i) => (
        <div key={i} className="flex gap-1">
          <input className="w-2/5 rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-1 py-0.5" value={k} onChange={(e) => setKey(k, e.target.value)} placeholder="clé" />
          <input className="flex-1 rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-1 py-0.5" value={String(v ?? "")} onChange={(e) => setVal(k, e.target.value)} placeholder="valeur" />
          <button onClick={() => { const o = { ...value }; delete o[k]; onChange(o); }} className="rounded px-1.5 text-[var(--color-danger)] hover:bg-[var(--color-surface-elevated)]">×</button>
        </div>
      ))}
    </div>
  );
}

function coerce(raw: string): unknown {
  if (raw === "true") return true;
  if (raw === "false") return false;
  if (raw !== "" && !isNaN(Number(raw)) && /^-?\d*\.?\d+$/.test(raw)) return Number(raw);
  return raw;
}

function EffectsEditor({ value, onChange }: { value: EffectSpec[]; onChange: (v: EffectSpec[]) => void }) {
  const set = (i: number, patch: Partial<EffectSpec>) => onChange(value.map((e, idx) => (idx === i ? { ...e, ...patch } : e)));
  const add = () => onChange([...value, { position: "caster", effect: "particles", params: {} }]);
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between"><span className="text-[var(--color-foreground-subtle)]">Effets (VFX / SFX)</span>
        <button onClick={add} className="rounded bg-[var(--color-accent)] px-2 py-0.5 text-[11px] text-white">+ Effet</button></div>
      {value.map((fx, i) => (
        <div key={i} className="rounded border border-[var(--color-border)] p-2 space-y-1">
          <div className="flex gap-1">
            <select value={fx.position} onChange={(e) => set(i, { position: e.target.value as EffectSpec["position"] })} className="flex-1 rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-1 py-0.5">
              {EFFECT_POSITIONS.map((p) => <option key={p}>{p}</option>)}</select>
            <select value={fx.effect} onChange={(e) => set(i, { effect: e.target.value })} className="flex-1 rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-1 py-0.5">
              {EFFECT_TYPES.map((t) => <option key={t}>{t}</option>)}</select>
            <button onClick={() => onChange(value.filter((_, idx) => idx !== i))} className="rounded px-1.5 text-[var(--color-danger)] hover:bg-[var(--color-surface-elevated)]">×</button>
          </div>
          {fx.effect === "particles" && <>
            <input className={inputCls} placeholder="particule (flame, block, dust_color_transition…)" value={fx.particle ?? ""} onChange={(e) => set(i, { particle: e.target.value })} />
            <div className="flex gap-1">
              <input className={inputCls} type="number" placeholder="count" value={fx.count ?? ""} onChange={(e) => set(i, { count: e.target.value === "" ? undefined : Number(e.target.value) })} />
              <input className={inputCls} placeholder="material (si block/item)" value={fx.material ?? ""} onChange={(e) => set(i, { material: e.target.value || undefined })} />
            </div>
          </>}
          {fx.effect === "sound" && <div className="flex gap-1">
            <input className={inputCls} placeholder="sound" value={fx.sound ?? ""} onChange={(e) => set(i, { sound: e.target.value })} />
            <input className={inputCls} type="number" placeholder="vol" value={fx.volume ?? ""} onChange={(e) => set(i, { volume: e.target.value === "" ? undefined : Number(e.target.value) })} />
            <input className={inputCls} type="number" placeholder="pitch" value={fx.pitch ?? ""} onChange={(e) => set(i, { pitch: e.target.value === "" ? undefined : Number(e.target.value) })} />
          </div>}
          {fx.effect === "entity" && <EntityEditor value={fx.entitySpec} onChange={(es) => set(i, { entitySpec: es })} />}
          {fx.effect === "effectlib" && <input className={inputCls} placeholder="class EffectLib (ex. Cone, Sphere, Helix)" value={fx.effectlibClass ?? ""} onChange={(e) => set(i, { effectlibClass: e.target.value })} />}
          <KVEditor title="params (offsets, spreads…)" value={fx.params ?? {}} onChange={(p) => set(i, { params: p })} />
        </div>
      ))}
    </div>
  );
}

function EntityEditor({ value, onChange }: { value?: EffectSpec["entitySpec"]; onChange: (v: EffectSpec["entitySpec"]) => void }) {
  const es = value ?? { entity: "item_display", duration: 20, keyframes: [], extra: {} };
  const set = (patch: Partial<NonNullable<EffectSpec["entitySpec"]>>) => onChange({ ...es, ...patch });
  const t = es.transformation ?? {};
  const setT = (patch: Record<string, string | undefined>) => set({ transformation: { ...t, ...patch } });
  return (
    <div className="space-y-1 rounded bg-[var(--color-surface)] p-1">
      <div className="flex gap-1">
        <select value={es.entity} onChange={(e) => set({ entity: e.target.value })} className="flex-1 rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-1 py-0.5">
          {["item_display", "block_display", "text_display"].map((x) => <option key={x}>{x}</option>)}</select>
        <input className={inputCls} type="number" placeholder="duration" value={es.duration} onChange={(e) => set({ duration: Number(e.target.value) })} />
      </div>
      <input className={inputCls} placeholder="item (magic-item ou item vanilla)" value={es.item ?? ""} onChange={(e) => set({ item: e.target.value || undefined })} />
      <div className="flex gap-1">
        <input className={inputCls} placeholder="scale (2,2,2)" value={t.scale ?? ""} onChange={(e) => setT({ scale: e.target.value || undefined })} />
        <input className={inputCls} placeholder="translation (0,1,0)" value={t.translation ?? ""} onChange={(e) => setT({ translation: e.target.value || undefined })} />
      </div>
      <div className="flex gap-1">
        <input className={inputCls} placeholder="left-rotation quat" value={t.leftRotation ?? ""} onChange={(e) => setT({ leftRotation: e.target.value || undefined })} />
        <select value={es.billboard ?? ""} onChange={(e) => set({ billboard: (e.target.value || undefined) as never })} className={inputCls}>
          <option value="">billboard</option>{["none", "fixed", "vertical", "horizontal", "center"].map((b) => <option key={b}>{b}</option>)}</select>
      </div>
      <p className="text-[10px] text-[var(--color-foreground-muted)]">{es.keyframes.length} keyframe(s) — édition avancée via params bientôt.</p>
    </div>
  );
}

export default function AbilitiesPage() {
  return (
    <ReactFlowProvider>
      <EditorInner />
    </ReactFlowProvider>
  );
}
