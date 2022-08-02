console.redhat.com CloudEvents
==============================

This repo contains jsonschema definitions for events generated by console.redhat.com apps.

Layout
------

* `schemas/events/v1/events.json` defines the overall schema for all console.redhat.com CloudEvents.
  All apps SHOULD produce events matching this schema, with the `data` property being app-specific.
  The `dataschema` field MUST be used to specify the schema of `data`.
* `schemas/apps` contains versioned subdirectories for each app's event `data` schemas.
  * e.g. `schemas/apps/advisor/v1` contains advisor event `data` schema
* `schemas/core` contains versioned event data schemas for common subjects (e.g. systems). An app
  may use a core schema during development, but will usually need its own schema to contain
  additional app-specific data.

Adding a new schema
-------------------

If the subject is a common object across console.redhat.com, then it should be defined in the `core`
directory.

Any app-specific data should be defined in an app-specific schema in the `apps` directory.

New schemas MUST be added to `schemas/events/v1/events.json` -> `data` -> `oneOf` section.


Schemas SHOULD make liberal use of `"additionalProperties": false` to enforce deliberate changes to
properties.

Updating an existing schema
---------------------------

Updates SHOULD be backwards compatible. If a backwards incompatible change is needed, you must
coordinate with consumers, and you SHOULD bump the version number for clarity.

Docs
----

See https://redhatinsights.github.io/event-schemas/ for generated documentation.
Documentation can be generated locally via `scripts/update-docs.sh`.

Scripts
-------

* `scripts/update-docs.sh` generates the `docs/index.html` and related files.
  If you do not run `npm install` first, it will fallback to the asyncapi
  generator container image, which is slower than direct node usage, but
  functionally equivalent.
* `scripts/validate.py` validates both the overall JSON, and that the `data` property validates 
  against the specified `dataschema`. It requires the python `jsonschema` package 
  (`dnf install python3-jsonschema` or `pip install --user jsonschema`).

```
cat examples/advisor.json | python scripts/validate.py
```
