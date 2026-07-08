# Shared scripts

Docker runners and retail data generators used by `integration-tests/` and `retail-example/`.

## Runners

```bash
./scripts/run-postgres.sh up
./scripts/run-hop.sh <hop-project-dir> <workflow>
```

| Project folder | Example |
|----------------|---------|
| `integration-tests` | `./scripts/run-hop.sh integration-tests tests/run-tests.hwf` |
| `retail-example` | `./scripts/run-hop.sh retail-example workflows/run-retail-initial.hwf` |

Thin wrappers also exist in `integration-tests/run-tests.sh` and `integration-tests/run-postgres.sh`.

## Retail data generators

`scripts/end-to-end/` — Python helpers invoked from `retail-example` workflows:

- `generate-retail-data.py` — initial snapshot or period update CSVs
- `activate-source-wave.py` — switch catalog file masks
- `generate-catalog-sources.py` — (re)create E2E catalog JSON entries

## Video tutorials

Rebuild a narrated tutorial MP4 from `docs/video-tutorials/<name>/` using xAI TTS and MoviePy:

```bash
export XAI_API_KEY=your-key-here

# Quick test (first 3 scenes)
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --limit 3

# Full build
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --locale en

# Video only from cached TTS
./scripts/rebuild-video-tutorial.sh your-first-data-vault-model --video-only --locale en
```

The script creates `scripts/video/.venv` on first run and installs `moviepy`, `pydub`, `requests`, and `PyYAML`. Video builds require `ffmpeg` on `PATH`.

Each scene lasts for the spoken audio plus a 1 second pause (configurable with `--pause-after-sec`). See `docs/video-tutorials/your-first-data-vault-model/README.md`.

Lower-level entry point: `scripts/video/build_moviepy_video.py`.