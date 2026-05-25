# Operator UI architecture notes

Use this folder for architecture notes that describe the future optional operator-facing UI layer.

## Purpose

This folder is the landing zone for admin, monitoring, scheduling, and job-authoring user-experience architecture.

Use it when you want to understand:

- how a future UI should relate to the ETL worker and optional control plane
- which UI capabilities belong in early MVP versus later phases
- how monitoring, schedule management, and job authoring should stay aligned with the selected-job runtime contract

## Current anchor notes

- [`./operator-ui-architecture-direction.md`](./operator-ui-architecture-direction.md) - first UI architecture direction for admin, monitoring, schedule management, and job authoring
- [`./angular-ui-mvp-structure.md`](./angular-ui-mvp-structure.md) - practical Angular-based MVP structure for routes, components, phases, and control-plane-facing API clients
- [`./angular-ui-mvp-wireframes.md`](./angular-ui-mvp-wireframes.md) - low-fidelity wireframes for the first five Angular MVP screens and their operator drill-down flows
- [`../control-plane/scheduler-architecture-direction.md`](../control-plane/scheduler-architecture-direction.md) - scheduler backend direction that the UI should manage rather than replace

## Design rule

The operator UI is optional.

It should sit on top of control-plane APIs and runtime evidence. It must not bypass the selected `etl.config.job -> job-config.yaml` launch contract or create a second hidden orchestration model.

