{
  description = "Run ZCode on NixOS";

  inputs.nixpkgs.url =
    "github:NixOS/nixpkgs/ef34387ddd751e1ab8857adf4676492d32eb24ec";

  outputs = { nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;
      };

      pname = "zcode";
      version = "3.11.2";

      src = pkgs.fetchurl {
        url = "https://cdn-zcode.z.ai/zcode/electron/releases/${version}/linux-x64/ZCode-${version}-linux-x64.AppImage";
        hash = "sha256-/EzIUShqQOqAkM6/qsGp+b3ETqs0njbu8NOzIZ85MD8=";
      };

      appimageContents = pkgs.appimageTools.extract {
        inherit pname version src;
      };

      zcode = pkgs.appimageTools.wrapType2 {
        inherit pname version src;

        nativeBuildInputs = [ pkgs.makeWrapper ];

        extraInstallCommands = ''
          install -m 444 -D \
            ${appimageContents}/zcode.desktop \
            $out/share/applications/zcode.desktop

          substituteInPlace $out/share/applications/zcode.desktop \
            --replace-fail 'Exec=AppRun --no-sandbox %U' 'Exec=zcode %U'

          cp -r ${appimageContents}/usr/share/icons $out/share/

          mv $out/bin/zcode $out/bin/.zcode-wrapped
          makeWrapper $out/bin/.zcode-wrapped $out/bin/zcode \
            --add-flags '--no-sandbox'
        '';

        meta = {
          description = "Official ZCode desktop app for agentic development";
          homepage = "https://zcode.z.ai/en";
          license = pkgs.lib.licenses.unfree;
          mainProgram = "zcode";
          platforms = [ system ];
          sourceProvenance = [ pkgs.lib.sourceTypes.binaryNativeCode ];
        };
      };
    in
    {
      packages.${system} = {
        inherit zcode;
        default = zcode;
      };

      apps.${system} = {
        zcode = {
          type = "app";
          program = "${zcode}/bin/zcode";
        };
        default = {
          type = "app";
          program = "${zcode}/bin/zcode";
        };
      };
    };
}
