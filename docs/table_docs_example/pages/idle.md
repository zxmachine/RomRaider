# Idle speed control

This is a made-up page that shows what the documentation panel can display. The engine and its tables are
invented; the names match the example index ([table_docs.xml](../table_docs.xml)).

The idle controller holds the engine speed at `tbl_Idle_Target`. Fuel during idle is covered on the
[fuel page](fuel.md#base-fuel).

## Target speed

The target comes from the coolant temperature:

| Coolant (°C) | Target (rpm) | Note |
| ---: | ---: | --- |
| -20 | 1,400 | cold start |
| 20 | 1,000 | |
| 80 | 750 | warm |

![The idle target against coolant temperature](idle/target.svg)

## Control loop

```mermaid
flowchart TD
    A(["Every 16 ms"]) --> B{"Throttle closed?"}
    B -- no --> C["Hold the valve at tbl_Idle_Valve_Base"]
    B -- yes --> D["Error = target - engine speed"]
    D --> E["Valve = base + gain × error"]
    E --> F[/"Idle air valve"/]
```

The gain is `tbl_Idle_Gain`; the base position is `tbl_Idle_Valve_Base`.

## States

```mermaid
stateDiagram-v2
    [*] --> Cranking
    Cranking --> AfterStart: engine speed above 500 rpm
    AfterStart --> Idle: 10 s elapsed
    Idle --> OffIdle: throttle opened
    OffIdle --> Idle: throttle closed
    Idle --> [*]: engine stopped
```

In `AfterStart` the target is raised by `tbl_Idle_After_Start_Increase`.

## Code example

```c
valve = base + gain * (target - rpm);
```

See also: [the project page](https://github.com/RomRaider/RomRaider).
