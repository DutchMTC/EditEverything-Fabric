![Logo](https://cdn.modrinth.com/data/cached_images/28c59c57ce8075c46b6bd3032e4e6a5767346837.png)

**Edit Everything** is a comprehensive Minecraft Fabric mod designed to enhance your Creative Mode experience. Based on the "[Advanced Creative Tab](https://modrinth.com/mod/act)" Mod by ATE47, this mod provides a suite of powerful tools for editing items, managing inventories, and utilizing useful commands to streamline your creative workflow.

Whether you are a map maker, a server admin, or just someone who loves to experiment with custom items, Edit Everything gives you the control you need.

## Features

### 🛠️ Advanced Item Editor
Modify every aspect of your items with an intuitive GUI.

![General Item Editor Menu](https://cdn.modrinth.com/data/cached_images/a45b3a1bc70dcae5b64253516c3f5cfc9cab580f.png)

#### Meta & Data Components
The Meta tab serves as a hub for advanced item properties.
- **Unbreakable:** Toggle the unbreakable tag.
- **Data Components:** Add, edit, or remove modern data components
- **NBT Editor:** Edit raw NBT data directly with a tree-view editor.

![Meta Editor](https://cdn.modrinth.com/data/cached_images/3e288c481aaa0ec5a6c401915edfdcd5e1ee4d11.png)
![Data Component Editor](https://cdn.modrinth.com/data/cached_images/4fd00c6cc51bd2a395a3eb6815efb25305781369_0.webp)

#### Adventure Mode (Can Place / Can Break)
Easily configure the `CanPlaceOn` and `CanDestroy` tags for Adventure mode map making. Select blocks from a list to define where items can be used.
![Adventure Editor Screenshot](path/to/adventure_editor_screenshot.png)

#### Container Editor
Edit the contents of containers (Chests, Shulker Boxes, Barrels, etc.) directly without placing them.
![Chest Editor](https://cdn.modrinth.com/data/cached_images/d0f2b2792d971098332ea38e71af9172324d5151.png)

#### Command Block Editor
Edit Command Block items (Command, Name, Auto-Execute) directly in your inventory.
![Command Block Editor](https://cdn.modrinth.com/data/cached_images/0c3869b31261eba61f8ee2114fca90efb406449c.png)

#### Attributes
Customize attack damage, attack speed, max health, movement speed, and other attributes.
![Attributes Editor](https://cdn.modrinth.com/data/cached_images/84e519991df3833e7c2b51f559768373770712bc_0.webp)

#### Enchantments
Add any enchantment at any level, even those not normally compatible.
![Enchantments Editor](https://cdn.modrinth.com/data/cached_images/2ef28ae353a12fc76acadeb8ff00b67f273701f8_0.webp)

#### Potion Effects
Create custom potions with specific effects, durations, amplifiers, and custom colors.
![Potion Editor](https://cdn.modrinth.com/data/cached_images/b1b1394bbe9f3cadc7004bd0a383e3748c97603e_0.webp)

#### Fireworks
Design custom firework rockets with multiple explosions, colors, and fade effects.
![Firework Editor](https://cdn.modrinth.com/data/cached_images/d6186a71d9491bd8a185e7f0034171c33c79dbae_0.webp)

#### Player Heads
Get heads with specific textures or player names.
![Player Head Editor](https://cdn.modrinth.com/data/cached_images/72256d8b04d1e3efb9398963df816a98831659b0.png)

#### Entity Editor
Customize spawn egg entities and their equipment.
![Entity Editor](https://cdn.modrinth.com/data/cached_images/3d37c45e3a13727492fda81405c7a494c3fc9dca.png)

#### Armor Stand Editor
Edit Armor Stand items directly in your inventory. Configure the pose, equipment, and properties *before* you place it.
- **Pose:** Adjust the pose of every body part (Head, Body, Arms, Legs) with precision sliders.
- **Equipment:** Set the armor and held items.
- **Properties:** Toggle flags like Invisible, Glowing, Small, Show Arms, No Base Plate, and more.
- **Locks:** Configure interaction locks to prevent players from taking or swapping items.

*Note: You can also use `/ee armorstand` on placed armor stands to copy them as an item. Applying changes directly to the entity requires server permissions.*

### 📦 Item Giver
A powerful alternative to the standard Creative Inventory.
- Search and filter items easily.
- Access custom items and saved palettes.
![Giver Menu](https://cdn.modrinth.com/data/cached_images/877a7b535a766a2246f2ed31e7fd3bdb78c2318d_0.webp)

### 📑 Creative Tab
Save items you made to a Creative Tab
![Creative Tab](https://cdn.modrinth.com/data/cached_images/447b116df2cb203cd708c350053e1b62530d7af5_0.webp)

### 🎨 Color & Formatting
- **Color Modifier:** Change the color of leather armor, potions, and more with a visual picker.
- **Chat Formatting:** Built-in reference for chat color codes (`/ee format`).
- **Palette:** Copy-paste color codes easily (`/ee palette`).
![Color Picker](https://cdn.modrinth.com/data/cached_images/73a056771202b7eaf4b441891089d4f666456323.png)

### 🎮 Enhanced Gamemode Switcher (F3 + F4)
Edit Everything enhances the vanilla F3 + F4 gamemode switcher. It allows you to use this shortcut to switch gamemodes even if you don't have standard permission for the vanilla gamemode commands, provided the server supports the switch (or if you are in a singleplayer world where the mod handles the logic).

## Commands

The main command is `/ee` (or `/editeverything`).

| Command | Alias | Description |
| :--- | :--- | :--- |
| `/ee menu` | `/ee om` | Opens the main mod menu. |
| `/ee edit` | `/ee e` | Opens the Item Editor / Giver interface. |
| `/ee give <item>` | `/ee g` | Give yourself an item (supports custom NBT). |
| `/ee opengiver` | | Opens the Item Giver GUI directly. |
| `/ee instantclick` | | Toggles Instant Click (Instant Mine) mode. |
| `/ee instantplace` | | Toggles Instant Place mode. |
| `/ee color` | | Opens the Color Modifier for the held item. |
| `/ee enchant <id> <lvl>` | | Enchants the held item. |
| `/ee rename <name>` | | Renames the held item. Supports `&` for color codes. |
| `/ee unbreakable [bool]` | | Toggles the Unbreakable tag on the held item. |
| `/ee head <name>` | | Gives you the head of the specified player. |
| `/ee armorstand` | `/ee as` | Opens the Armor Stand Editor for the target. |
| `/ee randomfireworks` | `/ee rfw` | Gives a randomly generated firework rocket. |
| `/ee info` | | Displays mod version, authors, and license info. |
| `/ee format` | | Shows a list of chat formatting codes. |
| `/ee palette` | | Shows a color palette with copyable codes. |
| `/ee spectatortp <player>` | `/ee sptp` | Teleports to a player (useful for spectators). |

### Gamemode Shortcuts
Quickly switch gamemodes with these commands:
- `/gm <mode>` (e.g., `/gm creative`, `/gm 1`, `/gm c`)
- `/gmc` - Creative Mode
- `/gms` - Survival Mode
- `/gma` - Adventure Mode
- `/gmsp` - Spectator Mode

## Installation

1.  Install **Minecraft** (Version 1.21.10 - 1.21.11).
2.  Install **Fabric Loader**.
3.  Download **Edit Everything** and place the `.jar` file into your `mods` folder.
4.  Ensure you have the **Fabric API** installed.

## Credits

-   **DutchMTC**: Author / Maintainer
-   **ATE47**: Creator of the original "Advanced Creative Tab" mod.

## License

This project is licensed under the **LGPL-3.0** License.

![License Badge](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)
