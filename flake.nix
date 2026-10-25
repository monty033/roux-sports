{
  description = "Roux Sports - Android development shell (JDK 21 + Android SDK)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs =
    { nixpkgs, ... }:
    let
      # Validated on x86_64-linux only. Other systems are not supported and
      # would need their own androidenv composition.
      system = "x86_64-linux";

      pkgs = import nixpkgs {
        inherit system;
        config = {
          android_sdk.accept_license = true;
          allowUnfree = true;
        };
      };

      androidSdk =
        (pkgs.androidenv.composeAndroidPackages {
          platformVersions = [
            "37"
            "36"
            "35"
          ];
          buildToolsVersions = [ "36.0.0" ];
          includeEmulator = false;
          includeSystemImages = false;
          includeSources = false;
          includeNDK = false;
        }).androidsdk;
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          pkgs.git
          pkgs.curl
        ];

        JAVA_HOME = "${pkgs.jdk21.home}";
        ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/36.0.0/aapt2";

        shellHook = ''
          export PATH="${androidSdk}/libexec/android-sdk/build-tools/36.0.0:$PATH"
        '';
      };
    };
}
