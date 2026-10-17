fn main() {
    println!("cargo:rerun-if-changed=vendor/unrar");
    println!("cargo:rerun-if-changed=harbor_layout.cpp");
    // cfg!() in a build script describes the host, so read the target from
    // cargo; otherwise an Android build on Windows compiles the Windows units.
    let target_os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    let target_env = std::env::var("CARGO_CFG_TARGET_ENV").unwrap_or_default();
    let windows = target_os == "windows";
    if windows {
        println!("cargo:rustc-flags=-lpowrprof");
        println!("cargo:rustc-link-lib=shell32");
        println!("cargo:rustc-link-lib=advapi32");
        if target_env == "gnu" {
            println!("cargo:rustc-link-lib=pthread");
        }
    } else if target_os != "android" {
        // Android's libc carries pthreads; there is no separate libpthread.
        println!("cargo:rustc-link-lib=pthread");
    }
    let mut units: Vec<&str> = vec![
        "strlist",
        "strfn",
        "pathfn",
        "smallfn",
        "global",
        "file",
        "filefn",
        "filcreat",
        "archive",
        "arcread",
        "unicode",
        "system",
        "crypt",
        "crc",
        "rawread",
        "encname",
        "match",
        "timefn",
        "rdwrfn",
        "consio",
        "options",
        "errhnd",
        "rarvm",
        "secpassword",
        "rijndael",
        "getbits",
        "sha1",
        "sha256",
        "blake2s",
        "hash",
        "extinfo",
        "extract",
        "volume",
        "list",
        "find",
        "unpack",
        "headers",
        "threadpool",
        "rs16",
        "cmddata",
        "ui",
        "filestr",
        "scantree",
        "dll",
        "qopen",
        "largepage",
    ];
    if windows {
        units.push("isnt");
        // rar.hpp only declares MarkOfTheWeb under PROPAGATE_MOTW, and the Zone.Identifier
        // stream it reads is an NTFS feature, so this unit has nothing to compile elsewhere.
        units.push("motw");
    }
    let files: Vec<String> = units.iter().map(|s| format!("vendor/unrar/{s}.cpp")).collect();
    cc::Build::new()
        .cpp(true) // Switch to C++ library compilation.
        .opt_level(2)
        .std("c++14")
        // by default cc crate tries to link against dynamic stdlib, which causes problems on windows-gnu target
        .cpp_link_stdlib(None)
        .warnings(false)
        .extra_warnings(false)
        .flag_if_supported("-stdlib=libc++")
        .flag_if_supported("-fPIC")
        .flag_if_supported("-Wno-switch")
        .flag_if_supported("-Wno-parentheses")
        .flag_if_supported("-Wno-macro-redefined")
        .flag_if_supported("-Wno-dangling-else")
        .flag_if_supported("-Wno-logical-op-parentheses")
        .flag_if_supported("-Wno-unused-parameter")
        .flag_if_supported("-Wno-unused-variable")
        .flag_if_supported("-Wno-unused-function")
        .flag_if_supported("-Wno-missing-braces")
        .flag_if_supported("-Wno-unknown-pragmas")
        .flag_if_supported("-Wno-deprecated-declarations")
        .define("_FILE_OFFSET_BITS", Some("64"))
        .define("_LARGEFILE_SOURCE", None)
        // Keep archive decoding on one CPU; no UnRAR worker pool.
        .define("RARDLL", None)
        .files(&files)
        .file("harbor_layout.cpp")
        .compile("libunrar.a");
}
