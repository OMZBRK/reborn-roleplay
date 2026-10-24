fn main() {
    let mut attributes = tauri_build::Attributes::new();
    if embed_app_manifest() {
        // Le manifeste est déjà donné à l'éditeur de liens pour tous les
        // exécutables : celui de tauri-build ferait doublon (ressource
        // MANIFEST en double = échec de l'édition des liens).
        attributes = attributes
            .windows_attributes(tauri_build::WindowsAttributes::new_without_app_manifest());
    }
    tauri_build::try_build(attributes).expect("échec du script de build tauri");
}

/// Manifeste Windows (Common Controls v6) donné à l'éditeur de liens pour
/// TOUS les exécutables, tests compris.
///
/// L'icône de la zone de notification (feature `tray-icon`) importe des
/// fonctions de Common Controls v6 (`TaskDialogIndirect`…). tauri-build ne
/// met son manifeste que dans le binaire du launcher : l'exécutable de
/// `cargo test --lib` mourait au chargement (STATUS_ENTRYPOINT_NOT_FOUND).
/// `rustc-link-arg-tests` ne vise pas les tests unitaires d'une lib, d'où ce
/// contournement (celui des exemples du dépôt Tauri). Contenu identique au
/// manifeste par défaut de tauri-build. Renvoie `true` s'il est en place.
fn embed_app_manifest() -> bool {
    const MANIFEST: &str = r#"<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
  <dependency>
    <dependentAssembly>
      <assemblyIdentity
        type="win32"
        name="Microsoft.Windows.Common-Controls"
        version="6.0.0.0"
        processorArchitecture="*"
        publicKeyToken="6595b64144ccf1df"
        language="*"
      />
    </dependentAssembly>
  </dependency>
</assembly>
"#;
    // Éditeur de liens MSVC uniquement (options /MANIFEST*) ; ailleurs,
    // tauri-build garde la main.
    if std::env::var("CARGO_CFG_TARGET_ENV").as_deref() != Ok("msvc") {
        return false;
    }
    let Some(out_dir) = std::env::var_os("OUT_DIR") else {
        return false;
    };
    let path = std::path::Path::new(&out_dir).join("app.manifest");
    if let Err(e) = std::fs::write(&path, MANIFEST) {
        println!(
            "cargo:warning=manifeste Windows non écrit ({e}) : celui de tauri-build est utilisé"
        );
        return false;
    }
    println!("cargo:rustc-link-arg=/MANIFEST:EMBED");
    println!("cargo:rustc-link-arg=/MANIFESTINPUT:{}", path.display());
    true
}
