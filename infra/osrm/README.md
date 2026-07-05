# OSRM data

The `osrm` Docker Compose service expects preprocessed OSRM files in this directory:

```text
infra/osrm/data/toulouse.osrm
```

Prepare data from a Toulouse-compatible `.osm.pbf` extract:

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

Without prepared OSRM files, the backend falls back to simulated multi-segment routes.
