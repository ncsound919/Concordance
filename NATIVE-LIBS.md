# Native libraries not stored in git

Three prebuilt llama.cpp libraries are larger than the Gitea upload cap and are
therefore gitignored. They are **required** for a runtime-complete APK; a clean
checkout can run unit tests and assemble a debug APK, but the on-device model will
not load until they are restored.

| File | Size | SHA-256 |
|---|---|---|
| `app/src/main/jniLibs/arm64-v8a/libllama-common.so` | 84.4 MiB | `532c82cdfed7df7ead4f3bf5e6cddb823311ccfc6c224a4a466acca4be01628b` |
| `app/src/main/jniLibs/arm64-v8a/libllama-server-impl.so` | 60.1 MiB | `fa1dd82df81afd1cf7168cd3423276eebe1d8ee6c4f3dcd39910e34f8781a599` |
| `app/src/main/jniLibs/arm64-v8a/libllama.so` | 36.1 MiB | `a1717c0b129206dc3428f02b8610e7c3d5cb0e0baad32363b99770d3cd7db2f8` |

Copy them from a machine that already built the app, then verify:

```bash
sha256sum app/src/main/jniLibs/arm64-v8a/libllama*.so
```

The smaller natives (`libggml-*`, `libmtmd.so`, `libc++_shared.so`) and
`sherpa-onnx-1.13.8.aar` are tracked with Git LFS (147 MB across 21 objects).

## Why not LFS for these three

They were attempted through LFS first; the host returned HTTP 502 for each object
above ~30 MB while the 21 smaller objects uploaded. Splitting or external hosting
is the remaining option if CI must build a runtime-complete APK from a cold clone.
