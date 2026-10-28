# OSRM data

The default Compose stack automatically downloads and prepares routing data in the `osrm-data` Docker volume. Run `docker compose up -d --build`; no manual setup is required.

`osrm-download` downloads the extract to a temporary file before renaming it. `osrm-init` runs extraction, partitioning and customization with two processing threads and writes a completion marker only after success. `osrm` mounts the result read-only, and the backend waits for its health check.

Follow progress with `docker compose logs -f osrm-download osrm-init osrm`. The first preparation may take several minutes. Subsequent starts reuse it. `docker compose down` retains data; `docker compose down -v` deletes it.

The following optional scripts prepare host files for manual setups, independently of the default Compose volume:

```text
infra/osrm/data/toulouse.osrm
```

Prepare data from a Midi-Pyrenees `.osm.pbf` extract:

```bash
./scripts/setup-osrm.sh
```

On Windows PowerShell:

```powershell
.\scripts\setup-osrm.ps1
```

Then start the stack with OSRM:

```bash
docker compose up -d --build
```

If automatic preparation fails, inspect `docker compose logs osrm-download osrm-init osrm`, fix the reported cause and rerun Compose. The backend will not start until routing is healthy. When OSRM is enabled, failed routing requests are retried and reported rather than replaced with artificial routes.
