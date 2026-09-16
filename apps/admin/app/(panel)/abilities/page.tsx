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
  updateTechnique,
  validateTechnique,
  type GraphNode,
  type GraphStatus,
  type NodeType,
  type Requirement,
  type RequirementType,
  type TechniqueGraphDoc,
  type TechniqueSummary,
} from "../../../lib/abilities";

/* ─────────────────────────── node catalogue ─────────────────────────── */

const NODE_META: Record<
  NodeType,
  { label: string; color: string; group: "flow" | "authority" | "effect" }
> = {
  trigger: { label: "Déclencheur", color: "#3b5bdb", group: "flow" },
  gate: { label: "Prérequis (Gate)", color: "#d97706", group: "authority" },
  cost: { label: "Coût", color: "#0891b2", group: "authority" },
  cooldown: { label: "Recharge", color: "#7c3aed", group: "authority" },
  mastery: { label: "Maîtrise", color: "#65a30d", group: "authority" },
  effectExternal: { label: "Effet · Moteur", color: "#dc2626", group: "effect" },
  particles: { label: "Particules", color: "#e11d48", group: "effect" },
  sound: { label: "Son", color: "#db2777", group: "effect" },
  damage: { label: "Dégâts", color: "#b91c1c", group: "effect" },
  status: { label: "Statut", color: "#9333ea", group: "effect" },
  block: { label: "Bloc temporaire", color: "#78716c", group: "effect" },
  sequence: { label: "Séquence", color: "#475569", group: "flow" },
  delay: { label: "Délai", color: "#64748b", group: "flow" },
};

const REQUIREMENT_TYPES: RequirementType[] = [
  "ability", "mastery", "rank", "skill", "level",
  "clan", "village", "affinity", "nature", "exam", "mentor",
];

type FieldSpec = {
  key: string;
  label: string;
  kind: "text" | "number" | "select" | "boolean";
  options?: string[];
};

const FIELDS: Partial<Record<NodeType, FieldSpec[]>> = {
  trigger: [
    { key: "method", label: "Entrée", kind: "select", options: ["LEFT_CLICK", "RIGHT_CLICK", "HOLD_SNEAK", "CLICK_SEQUENCE"] },
    { key: "itemType", label: "JutsuItem", kind: "text" },
  ],
  cost: [
    { key: "chakra", label: "Chakra", kind: "number" },
    { key: "stamina", label: "Endurance", kind: "number" },
  ],
  cooldown: [{ key: "ms", label: "Recharge (ms)", kind: "number" }],
  mastery: [
    { key: "perCast", label: "Gain / cast", kind: "number" },
    { key: "variantThreshold", label: "Seuil variante", kind: "number" },
    { key: "variantId", label: "Id variante", kind: "text" },
  ],
  effectExternal: [
    { key: "provider", label: "Moteur", kind: "select", options: ["magicspells", "mythicmobs", "raw"] },
    { key: "ref", label: "Réf (sort/skill)", kind: "text" },
    { key: "runAsPlayer", label: "Exécuter en tant que joueur", kind: "boolean" },
  ],
  particles: [
    { key: "particle", label: "Particule", kind: "text" },
    { key: "shape", label: "Forme", kind: "select", options: ["point", "cone", "ring", "line", "sphere"] },
    { key: "count", label: "Nombre", kind: "number" },
    { key: "length", label: "Longueur", kind: "number" },
    { key: "radius", label: "Rayon", kind: "number" },
    { key: "color", label: "Couleur", kind: "text" },
    { key: "toColor", label: "Couleur (fin)", kind: "text" },
    { key: "material", label: "Matériau (block/item)", kind: "text" },
  ],
  sound: [
    { key: "sound", label: "Son", kind: "text" },
    { key: "volume", label: "Volume", kind: "number" },
    { key: "pitch", label: "Pitch", kind: "number" },
  ],
  damage: [
    { key: "shape", label: "Forme", kind: "select", options: ["cone", "ring", "target"] },
    { key: "amount", label: "Dégâts", kind: "number" },
    { key: "length", label: "Portée", kind: "number" },
    { key: "radius", label: "Rayon", kind: "number" },
  ],
  status: [
    { key: "effect", label: "Effet de potion", kind: "text" },
    { key: "durationTicks", label: "Durée (ticks)", kind: "number" },
    { key: "amplifier", label: "Niveau", kind: "number" },
  ],
  block: [
    { key: "width", label: "Largeur", kind: "number" },
    { key: "lifetimeSeconds", label: "Durée (s)", kind: "number" },
  ],
  delay: [{ key: "ms", label: "Délai (ms)", kind: "number" }],
};

/* ─────────────────────────── RF <-> doc mapping ─────────────────────────── */

const RF_TYPE = "reborn";

function docToRf(doc: TechniqueGraphDoc): { nodes: Node[]; edges: Edge[] } {
  const nodes: Node[] = doc.nodes.map((n, i) => ({
    id: n.id,
    type: RF_TYPE,
    position: n.position ?? { x: 60 + (i % 4) * 220, y: 60 + Math.floor(i / 4) * 150 },
    data: { node: n },
  }));
  const edges: Edge[] = doc.edges.map((e) => ({
    id: `${e.from}__${e.to}`,
    source: e.from,
    target: e.to,
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
    edges: edges.map((e) => ({ from: e.source, to: e.target })),
  };
}

/* ─────────────────────────── custom node ─────────────────────────── */

function RebornNode({ data, selected }: NodeProps) {
  const n = (data as { node: GraphNode }).node;
  const meta = NODE_META[n.type];
  const summary = nodeSummary(n);
  return (
    <div
      style={{
        borderColor: selected ? meta.color : "var(--color-border-strong)",
        boxShadow: selected ? `0 0 0 2px ${meta.color}55` : "none",
      }}
      className="min-w-[150px] rounded-md border bg-[var(--color-surface-elevated)] text-[var(--color-foreground)]"
    >
      <Handle type="target" position={Position.Left} style={{ background: meta.color }} />
      <div
        className="rounded-t-md px-2 py-1 text-[11px] font-semibold uppercase tracking-wide"
        style={{ background: meta.color, color: "#fff" }}
      >
        {meta.label}
      </div>
      <div className="px-2 py-1.5 text-[12px] text-[var(--color-foreground-subtle)]">
        {summary}
      </div>
      <Handle type="source" position={Position.Right} style={{ background: meta.color }} />
    </div>
  );
}

function nodeSummary(n: GraphNode): string {
  switch (n.type) {
    case "trigger": return String(n.method ?? "LEFT_CLICK");
    case "gate": return `${(n.requires as unknown[] | undefined)?.length ?? 0} prérequis`;
    case "cost": return `${n.chakra ?? 0} chakra`;
    case "cooldown": return `${n.ms ?? 0} ms`;
    case "effectExternal": return `${n.provider ?? "?"} · ${n.ref ?? "?"}`;
    case "particles": return `${n.particle ?? "?"} ×${n.count ?? 0}`;
    case "sound": return String(n.sound ?? "?");
    case "damage": return `${n.amount ?? 0} (${n.shape ?? "cone"})`;
    case "status": return String(n.effect ?? "?");
    case "delay": return `${n.ms ?? 0} ms`;
    default: return "";
  }
}

const nodeTypes = { [RF_TYPE]: RebornNode };

/* ─────────────────────────── editor ─────────────────────────── */

let nodeCounter = 0;
function freshNodeId(type: NodeType): string {
  nodeCounter += 1;
  return `${type}_${Date.now().toString(36)}_${nodeCounter}`;
}

function emptyDoc(slug: string): TechniqueGraphDoc {
  return {
    id: slug,
    name: slug,
    category: "ninjutsu/pur",
    rank: "D",
    execution: "JUTSU",
    description: "",
    nodes: [{ id: freshNodeId("trigger"), type: "trigger", method: "LEFT_CLICK", position: { x: 80, y: 120 } }],
    edges: [],
  };
}

function EditorInner() {
  const [list, setList] = useState<TechniqueSummary[]>([]);
  const [current, setCurrent] = useState<{ dbId: string; status: GraphStatus; doc: TechniqueGraphDoc } | null>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const docRef = useRef<TechniqueGraphDoc | null>(null);

  const refreshList = useCallback(async () => {
    try {
      setList(await listTechniques());
    } catch (e) {
      toast.error(`Chargement impossible : ${(e as Error).message}`);
    }
  }, []);

  useEffect(() => {
    void refreshList();
  }, [refreshList]);

  const loadDoc = useCallback(
    (dbId: string, status: GraphStatus, doc: TechniqueGraphDoc) => {
      docRef.current = doc;
      setCurrent({ dbId, status, doc });
      const { nodes: n, edges: e } = docToRf(doc);
      setNodes(n);
      setEdges(e);
      setSelectedId(null);
    },
    [setNodes, setEdges],
  );

  const openTechnique = useCallback(
    async (id: string) => {
      try {
        const full = await getTechnique(id);
        loadDoc(full.id, full.status, full.graph);
      } catch (e) {
        toast.error(`Ouverture impossible : ${(e as Error).message}`);
      }
    },
    [loadDoc],
  );

  const onConnect = useCallback(
    (c: Connection) => setEdges((eds) => addEdge({ ...c, id: `${c.source}__${c.target}` }, eds)),
    [setEdges],
  );

  const addNode = useCallback(
    (type: NodeType) => {
      const id = freshNodeId(type);
      const node: GraphNode = { id, type, position: { x: 320, y: 80 + nodes.length * 40 } };
      if (type === "gate") node.requires = [];
      setNodes((nds) => [
        ...nds,
        { id, type: RF_TYPE, position: node.position!, data: { node } },
      ]);
      setSelectedId(id);
    },
    [nodes.length, setNodes],
  );

  const patchSelected = useCallback(
    (patch: Record<string, unknown>) => {
      setNodes((nds) =>
        nds.map((rf) => {
          if (rf.id !== selectedId) return rf;
          const n = (rf.data as { node: GraphNode }).node;
          return { ...rf, data: { node: { ...n, ...patch } } };
        }),
      );
    },
    [selectedId, setNodes],
  );

  const deleteSelected = useCallback(() => {
    if (!selectedId) return;
    setNodes((nds) => nds.filter((n) => n.id !== selectedId));
    setEdges((eds) => eds.filter((e) => e.source !== selectedId && e.target !== selectedId));
    setSelectedId(null);
  }, [selectedId, setNodes, setEdges]);

  const selectedNode = useMemo(() => {
    const rf = nodes.find((n) => n.id === selectedId);
    return rf ? (rf.data as { node: GraphNode }).node : null;
  }, [nodes, selectedId]);

  const buildDoc = useCallback((): TechniqueGraphDoc | null => {
    if (!current || !docRef.current) return null;
    return rfToDoc(docRef.current, nodes, edges);
  }, [current, nodes, edges]);

  const save = useCallback(async () => {
    if (!current) return;
    const doc = buildDoc();
    if (!doc) return;
    setBusy(true);
    try {
      await updateTechnique(current.dbId, {
        name: doc.name,
        category: doc.category,
        status: current.status,
        graph: doc,
      });
      docRef.current = doc;
      toast.success("Technique enregistrée.");
      void refreshList();
    } catch (e) {
      toast.error(`Échec de l'enregistrement : ${(e as Error).message}`);
    } finally {
      setBusy(false);
    }
  }, [current, buildDoc, refreshList]);

  const validate = useCallback(async () => {
    if (!current) return;
    await save();
    setBusy(true);
    try {
      const r = await validateTechnique(current.dbId);
      if (r.warnings.length) {
        toast.warning(`Valide, ${r.warnings.length} avertissement(s)`, {
          description: r.warnings.slice(0, 4).join(" · "),
        });
      } else {
        toast.success("Graphe valide (schéma + DAG + références).");
      }
    } catch (e) {
      toast.error(`Invalide : ${(e as Error).message}`);
    } finally {
      setBusy(false);
    }
  }, [current, save]);

  const deploy = useCallback(async () => {
    setBusy(true);
    try {
      const r = await deployTechniques();
      toast.success(
        `Déployé : ${r.deployed} technique(s), ${r.msSpells} sort(s) MS, ${r.mythicSkills} skill(s) MM`,
        { description: `Écrit : ${r.written.length} fichier(s) · reload : ${r.reloaded.join(", ")}` },
      );
    } catch (e) {
      toast.error(`Déploiement échoué : ${(e as Error).message}`);
    } finally {
      setBusy(false);
    }
  }, []);

  const newTechnique = useCallback(async () => {
    const slug = window.prompt("Identifiant (lower_snake_case) :")?.trim();
    if (!slug) return;
    if (!/^[a-z0-9_]+$/.test(slug)) {
      toast.error("Identifiant invalide (a-z, 0-9, _).");
      return;
    }
    setBusy(true);
    try {
      const doc = emptyDoc(slug);
      const created = await createTechnique({ slug, name: slug, category: doc.category, graph: doc });
      await refreshList();
      loadDoc(created.id, created.status, doc);
      toast.success(`Technique '${slug}' créée.`);
    } catch (e) {
      toast.error(`Création impossible : ${(e as Error).message}`);
    } finally {
      setBusy(false);
    }
  }, [refreshList, loadDoc]);

  const setStatus = useCallback(
    (status: GraphStatus) => setCurrent((c) => (c ? { ...c, status } : c)),
    [],
  );

  return (
    <div className="flex h-[calc(100vh-0px)] w-full">
      {/* left: technique list */}
      <aside className="w-64 shrink-0 border-r border-[var(--color-border)] bg-[var(--color-surface)] p-3 overflow-y-auto">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold">Techniques</h2>
          <button
            onClick={newTechnique}
            className="rounded bg-[var(--color-accent)] px-2 py-1 text-xs text-white hover:opacity-90"
          >
            + Nouvelle
          </button>
        </div>
        <ul className="space-y-1">
          {list.map((t) => (
            <li key={t.id}>
              <button
                onClick={() => openTechnique(t.id)}
                className={`w-full rounded px-2 py-1.5 text-left text-xs hover:bg-[var(--color-surface-elevated)] ${
                  current?.dbId === t.id ? "bg-[var(--color-surface-elevated)]" : ""
                }`}
              >
                <div className="font-medium truncate">{t.name}</div>
                <div className="text-[10px] text-[var(--color-foreground-muted)]">
                  {t.slug} · {t.status}
                </div>
              </button>
            </li>
          ))}
          {list.length === 0 && (
            <li className="text-xs text-[var(--color-foreground-muted)]">
              Aucune technique. Crée-en une.
            </li>
          )}
        </ul>
      </aside>

      {/* center: canvas */}
      <main className="relative flex-1 min-w-0">
        {current ? (
          <>
            <div className="absolute left-0 right-0 top-0 z-10 flex flex-wrap items-center gap-2 border-b border-[var(--color-border)] bg-[var(--color-surface)]/90 px-3 py-2 backdrop-blur">
              <span className="text-sm font-semibold">{current.doc.id}</span>
              <select
                value={current.status}
                onChange={(e) => setStatus(e.target.value as GraphStatus)}
                className="rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-2 py-1 text-xs"
              >
                <option value="DRAFT">DRAFT</option>
                <option value="PUBLISHED">PUBLISHED</option>
                <option value="ARCHIVED">ARCHIVED</option>
              </select>
              <div className="mx-2 h-4 w-px bg-[var(--color-border)]" />
              <PaletteMenu onAdd={addNode} />
              <div className="ml-auto flex gap-2">
                <button disabled={busy} onClick={save} className="rounded border border-[var(--color-border)] px-3 py-1 text-xs hover:bg-[var(--color-surface-elevated)] disabled:opacity-50">Enregistrer</button>
                <button disabled={busy} onClick={validate} className="rounded border border-[var(--color-border)] px-3 py-1 text-xs hover:bg-[var(--color-surface-elevated)] disabled:opacity-50">Valider</button>
                <button disabled={busy} onClick={deploy} className="rounded bg-[var(--color-accent)] px-3 py-1 text-xs text-white hover:opacity-90 disabled:opacity-50">Déployer</button>
              </div>
            </div>
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onNodeClick={(_, n) => setSelectedId(n.id)}
              onPaneClick={() => setSelectedId(null)}
              nodeTypes={nodeTypes}
              fitView
              proOptions={{ hideAttribution: true }}
              className="bg-[var(--color-background)]"
            >
              <Background color="#1f2430" gap={20} />
              <Controls />
              <MiniMap pannable zoomable className="!bg-[var(--color-surface)]" />
            </ReactFlow>
          </>
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-[var(--color-foreground-muted)]">
            Sélectionne ou crée une technique pour l'éditer.
          </div>
        )}
      </main>

      {/* right: inspector */}
      {selectedNode && (
        <aside className="w-72 shrink-0 border-l border-[var(--color-border)] bg-[var(--color-surface)] p-3 overflow-y-auto">
          <div className="mb-2 flex items-center justify-between">
            <h3 className="text-sm font-semibold">{NODE_META[selectedNode.type].label}</h3>
            <button onClick={deleteSelected} className="rounded px-2 py-1 text-xs text-[var(--color-danger)] hover:bg-[var(--color-surface-elevated)]">Supprimer</button>
          </div>
          <NodeInspector node={selectedNode} onPatch={patchSelected} />
        </aside>
      )}
    </div>
  );
}

function PaletteMenu({ onAdd }: { onAdd: (t: NodeType) => void }) {
  return (
    <div className="flex flex-wrap gap-1">
      {(Object.keys(NODE_META) as NodeType[])
        .filter((t) => t !== "trigger")
        .map((t) => (
          <button
            key={t}
            onClick={() => onAdd(t)}
            title={`Ajouter : ${NODE_META[t].label}`}
            className="rounded px-2 py-1 text-[11px] text-white hover:opacity-90"
            style={{ background: NODE_META[t].color }}
          >
            + {NODE_META[t].label}
          </button>
        ))}
    </div>
  );
}

function NodeInspector({
  node,
  onPatch,
}: {
  node: GraphNode;
  onPatch: (patch: Record<string, unknown>) => void;
}) {
  const specs = FIELDS[node.type] ?? [];
  return (
    <div className="space-y-3">
      {specs.map((f) => (
        <label key={f.key} className="block text-xs">
          <span className="mb-1 block text-[var(--color-foreground-subtle)]">{f.label}</span>
          {f.kind === "select" ? (
            <select
              value={String(node[f.key] ?? f.options?.[0] ?? "")}
              onChange={(e) => onPatch({ [f.key]: e.target.value })}
              className="w-full rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-2 py-1"
            >
              {f.options!.map((o) => (
                <option key={o} value={o}>{o}</option>
              ))}
            </select>
          ) : f.kind === "boolean" ? (
            <input
              type="checkbox"
              checked={Boolean(node[f.key])}
              onChange={(e) => onPatch({ [f.key]: e.target.checked })}
            />
          ) : (
            <input
              type={f.kind === "number" ? "number" : "text"}
              value={node[f.key] === undefined ? "" : String(node[f.key])}
              onChange={(e) =>
                onPatch({
                  [f.key]:
                    f.kind === "number"
                      ? e.target.value === "" ? undefined : Number(e.target.value)
                      : e.target.value,
                })
              }
              className="w-full rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-2 py-1"
            />
          )}
        </label>
      ))}
      {node.type === "gate" && (
        <RequirementsEditor
          value={(node.requires as Requirement[] | undefined) ?? []}
          onChange={(reqs) => onPatch({ requires: reqs })}
        />
      )}
    </div>
  );
}

function RequirementsEditor({
  value,
  onChange,
}: {
  value: Requirement[];
  onChange: (r: Requirement[]) => void;
}) {
  const set = (i: number, patch: Partial<Requirement>) =>
    onChange(value.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-[var(--color-foreground-subtle)] text-xs">Prérequis</span>
        <button
          onClick={() => onChange([...value, { type: "ability", id: "" }])}
          className="rounded bg-[var(--color-accent)] px-2 py-0.5 text-[11px] text-white"
        >
          + Ajouter
        </button>
      </div>
      {value.map((r, i) => (
        <div key={i} className="rounded border border-[var(--color-border)] p-2 space-y-1">
          <div className="flex gap-1">
            <select
              value={r.type}
              onChange={(e) => set(i, { type: e.target.value as RequirementType })}
              className="flex-1 rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-1 py-0.5 text-xs"
            >
              {REQUIREMENT_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
            <button
              onClick={() => onChange(value.filter((_, idx) => idx !== i))}
              className="rounded px-1.5 text-[var(--color-danger)] hover:bg-[var(--color-surface-elevated)]"
            >
              ×
            </button>
          </div>
          <input
            placeholder="id (ex. controle_chakra, katon, chunin)"
            value={r.id ?? ""}
            onChange={(e) => set(i, { id: e.target.value })}
            className="w-full rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-1 py-0.5 text-xs"
          />
          {(r.type === "mastery" || r.type === "skill" || r.type === "level") && (
            <input
              type="number"
              placeholder="min"
              value={r.min ?? ""}
              onChange={(e) => set(i, { min: e.target.value === "" ? undefined : Number(e.target.value) })}
              className="w-full rounded border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-1 py-0.5 text-xs"
            />
          )}
        </div>
      ))}
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
