# Combat Inventory Retention

A Spigot plugin that lets players keep 50% of their inventory when they die in combat. Deaths outside of combat still drop every item.

## Requirements

- Java 17 or newer
- Maven 3.8+
- Spigot/Paper server (api-version 1.20)

## Build

```powershell
mvn clean package
```

The shaded JAR is generated at `target/combat-inventory-retention-1.0.0-SNAPSHOT-shaded.jar`.

## Installation

1. Compile the plugin as shown above.
2. Copy the generated JAR into your server's `plugins` folder.
3. Restart the server.

## Behavior

- When a player has participated in combat within the last 15 seconds (as attacker or victim), they are considered "in combat".
- If they die during this window, 50% of their inventory stacks are stored and restored after respawn.
- If a player dies outside combat, no items are retained.
