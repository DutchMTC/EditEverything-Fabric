# Edit Everything

> This mod is fully client side! You only need to be in creative to use the give/modifier action, some features/fixes can be missing if you're not using the latest version of the mod for the latest Minecraft version.

# Features

## Global

- Tooltip: Repair cost, durability
- Container (like Shulker box): Show the inventory of a container by doing **CTRL+SHIFT**

## Creative

- item modifiers
- item give command
- create tab with every non obtainable item

## Spectator

- `/ee sptp <username>` - Teleport to `username` using the spectator game mode.

# Commands

- `/ee [help]` - show all commands
- `/ee edit` - Edit your inhand item
- `/ee head [name=you]` - Give a head by a name
- `/ee rename <name>` - Rename your inhand item
- `/ee rfw` - Get a random (legit seems) fireworks
- `/ee enchant [enchant] [level]` - Enchant the inhand item
- `/ee format [format]` - Get formatting help
- `/ee info` - Get mod information
- `/ee unbreakable [true|false]` - Set the inhand item unbreakable
- `/ee color` - Color command
  - `/ee color picker` - Set the color of the inhand item using the picker
  - `/ee color remove` - Remove the color of the inhand item
  - `/ee color set <color>` - Set the color of the inhand item
  - `/ee color set rgb <red> <green> <blue>` - Set with RGB (0-255,0-255,0-255)
  - `/ee color set hsl <hue> <saturation> <lightness>` - Set with HSL (0-355,0-100,0-100)
  - `/ee color set hex <hexcode>` - Set with hex code, example: FF0000 is red
- `/gm [mode]` - `/gamemode [mode]`
- `/gms` - `/gamemode survival`
- `/gmc` - `/gamemode creative`
- `/gma` - `/gamemode adventure`
- `/gmsp` - `/gamemode spectator`

The [Spectator Teleporter mod](https://www.curseforge.com/minecraft/mc-mods/gm3-teleporter) is integrated with the command

- `/ee sptp <username>` - Teleport using the gamemode spectator, you need to be in spectator mode or be able to do the /gamemode spectator command

# Inventory item viewer

View inside a container using the **CTRL + SHIFT** command.

![shulker example](https://i.imgur.com/dNjIUiF.png)

# Tool tips

By Shifting when you looking at an item in you inventory you can get some information

![tooltip](https://i.imgur.com/Wb9rpZr.png)

**Combat** : Creative tab

**Color** : Armor/Potion color

**Durability** (With colors) : How many time you can use your tool/armor

**RepairCost** : how much you need to repair/rename/enchant-it in a anvil

**Tags**(Count) : List of NBT tag of this item

**[Y]** (can be changed) : Open this item in the giver

**[N]** (can be changed) : Save this in your EE Menu

# Menus

![menu image](https://i.imgur.com/Vt2dpji.png)

Mod Menu, can be get with [N] (can be changed) or in mods list with the config button

**Left Click** : Open in the giver

**Right Click** : Give in your inventory (you must be in creative)

**Shift Left Click** : Copy

**Shift Right Click** : Delete

![giver image](https://i.imgur.com/mEoX2ci.png)

Giver menu, can be get with [Y] (can be changed) or in the menu by clicking on an item

**Give** : Give (you must be in creative)

**Copy** : Copy give information in you clipboard

**Item Editor** : open it in the item editor

**Delete** (if you are from the menu) : Delete this from config

**Save** (if you're not from the menu) : Save this in your config

**Cancel** (if you are from the menu) : Don't save edit

**Done** (if you are from the menu) : Save edit

**Done** (if you're not from the menu) : Close the menu

![item modifier image](https://i.imgur.com/uTAqvzW.png)

Item editor, can be get with [H] (can be changed) or in the giver

Here is a list of the changes made by the buttons : (type change Item's type ...)

![tool tip](https://i.imgur.com/sjq28vq.png)

You can add & to use color formatting in name and description. ([wiki](https://minecraft.fandom.com/wiki/Formatting_codes) or by doing the `/ee format` to get formatting info)

![meta](https://i.imgur.com/JtbKZLD.png)

Update the tag and the unbreakability of the item.

## Modifiers

From the item modifier, you can set data of the item.

### Head

You can update the link, the name or download the skin of player head.

![head](https://i.imgur.com/opy6DGM.png)

### Command block

Update name and command of command block.

![command block](https://i.imgur.com/KPuoGuO.png)

Command Block editor (type = Normal/Repeating/Chain/Minecart)

![command block give](https://i.imgur.com/lpoRZyV.png)

### Fireworks

![explosion list](https://i.imgur.com/J5dDN6i.png)

Fireworks editor

Firework star -> Change explosion

![explosion](https://i.imgur.com/k8Y58eU.png)

Explosion editor (See [Minecraft Wiki](https://www.curseforge.com/linkout?remoteUrl=https%253a%252f%252fminecraft.gamepedia.com%252fFirework_Rocket%2523Display_Properties) for more information)

### Potion

(+Tipped Arrow) Change effects, type and color

![potion editor](https://i.imgur.com/hiVpxX1.png)

**Duration** : in tick (1/20s)

**Amplifier** : level - 1, for example Speed with an amplifier of 1 is Speed II

**Ambient** : whether or not this is an effect provided by a beacon and therefore should be less intrusive on the screen. This tag is optional and defaults to 0. Due to a bug, it has no effect on splash potions.

**Show Particules** : whether or not this effect produces particles. This tag is optional and defaults to 1. Due to a bug, it has no effect on splash potions.

([Minecraft wiki](https://minecraft.fandom.com/wiki/Player.dat_format#Potion_Effects))

### Color

You can change the leather armor color, explosion color or the color of the potion.

![picker](https://i.imgur.com/ap07jdV.png)

![picker advanced](https://i.imgur.com/brXY5qo.png)

### Inventory

You can set the items in the inventory item

![inventory modifier](https://i.imgur.com/LuGVy7c.png)

# Edit Everything

![act](https://i.imgur.com/nj4eXmb.png)

This tab contain every non-findable items in creative

GUI Dev mode: **CTRL + ALT + K**

1.x versions depleted, please use for an 1.8 version [ATEHUD instead (1.8.9)](https://minecraft.curseforge.com/projects/atehud)
