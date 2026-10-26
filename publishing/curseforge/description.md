# CraftLink

**Turn a phone or tablet browser into a touch controller for your Java Edition world.** Scan a QR code shown in-game, open the controller in your browser, and play over your local network. No companion mobile app is required.

## Two ways to play

- **Control your player:** use your phone to move, look around, jump, sneak, sprint, attack, use items, and manage your hotbar.
- **Let friends join as bots:** host a room in a singleplayer world and give each phone its own player-shaped bot. Up to eight devices can join the room, with separate movement, inventory, names, and skins.

Bots run inside the host PC's world. They are not separate Microsoft accounts and do not connect to external servers as independent players.

## Touch controls and live view

- Joystick + tap, D-pad + tap, and joystick + crosshair control modes.
- Swipe to look around, tap to use or place, and hold to attack or mine.
- Multitouch movement and camera control.
- Customizable button positions, sizes, opacity, and touch sensitivity.
- Bot-view streaming with first-person hands and held items.
- Inventory, creative item tabs, hotbar selection, item dropping, and creative flight.
- Participant nicknames, built-in skins, and local skin PNG support.

## Requirements

- Minecraft Java Edition **1.20.1**.
- **Forge 47.4.10 or later in the 47.x series**, and **Java 17**.
- A phone or tablet with a browser that can reach the host PC over the local network.

Install CraftLink on the host/client PC. The phone does not need Minecraft, Java, a mod loader, or a separate app. The controller web files are included in the JAR, so there is no separate web server to set up.

## Getting started

1. Put the CraftLink JAR in your Forge instance's `mods` folder and launch the game.
2. Connect the PC and phone to a local network that allows devices to communicate.
3. Open a world and press **F8** to open CraftLink. The key can be changed in Minecraft's controls settings.
4. Start a normal controller connection, or open the participant room for separate bots.
5. Scan the QR code on the phone, complete any connection prompt shown on the PC, and use the controller in landscape orientation.
6. Use **F9** to pause all bot controls when needed.

A wired PC can be used with phones on the same LAN. Android USB connections are also supported through ADB reverse forwarding and require additional PC-side setup. This Android USB method does not support iPhone.

## Scope and current limitations

- **This public build is for LAN and supported USB connections.** It does not provide a public internet relay or connections from unrelated Wi-Fi networks or mobile data.
- Bot rooms require the PC hosting the integrated server, such as a singleplayer world or its LAN session. Normal controller mode can control your own player while connected to a multiplayer server.
- Live bot views share the host PC's rendering budget. More connected devices can reduce each device's frame rate. This is a streamed PC view, not a native copy of the game running on the phone.
- Fabulous graphics and shader mods are not supported or fully tested. Start with Fast or Fancy graphics.
- Vanilla content and selected mod integrations are supported, but mod-specific screens, player effects, and custom controls may require additional compatibility work. Compatibility with every mod is not guaranteed.
- Local-network restrictions, client isolation, or a firewall may prevent a phone from reaching the PC. LAN HTTP/WebSocket traffic is not encrypted; use a trusted network and keep invitation URLs private.

CraftLink is an independent project and is not an official product of Mojang or Microsoft.
