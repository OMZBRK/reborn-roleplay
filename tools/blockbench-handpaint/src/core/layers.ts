/**
 * Émission d'un TextureLayer éditable à partir d'un ImageData.
 * Toute passe générée (AO, lighting, gradient…) passe par ici → résultat
 * réordonnable / masquable / régénérable, jamais verrouillé.
 */

type BlendMode = 'multiply' | 'add' | 'screen' | 'default' | 'difference';

export function emitLayer(
  texture: Texture,
  name: string,
  blendMode: BlendMode,
  imageData: ImageData,
  undoName: string,
): void {
  const tex = texture as any;
  Undo.initEdit({ textures: [texture] } as any);
  if (!tex.layers_enabled) tex.activateLayers?.(false);

  const layer = new TextureLayer(
    { name, blend_mode: blendMode, opacity: 255 } as any,
    texture,
  );
  const l = layer as any;
  l.setSize(texture.width, texture.height);
  layer.ctx.putImageData(imageData, 0, 0);
  l.addForEditing();

  tex.updateChangesAfterEdit?.();
  Undo.finishEdit(undoName);
}

/** Composite un ImageData (multiply) directement dans la texture (pas un calque). */
export function paintMultiplyIntoTexture(
  texture: Texture,
  imageData: ImageData,
  editName: string,
): void {
  (texture as any).edit(
    (canvas: HTMLCanvasElement) => {
      const ctx = canvas.getContext('2d')!;
      const tmp = document.createElement('canvas');
      tmp.width = texture.width;
      tmp.height = texture.height;
      tmp.getContext('2d')!.putImageData(imageData, 0, 0);
      const prev = ctx.globalCompositeOperation;
      ctx.globalCompositeOperation = 'multiply';
      ctx.drawImage(tmp, 0, 0);
      ctx.globalCompositeOperation = prev;
    },
    { edit_name: editName },
  );
}
