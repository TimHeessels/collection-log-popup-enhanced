# CoX delay - test checklist

Most of this can be tested without a raid. Start the client with `./gradlew run` (developer mode). Type the commands in the chatbox.

- `::coxsim <item> [player]` posts the chat lines a CoX completion produces: the party line, the clan broadcast, and, if it's your drop, the valuable drop and collection log lines. Leave out `player` for your own drop.
- `::coxopen` does what opening the reward chest does.

## Setup
- [ ] Screenshot plugin on, "Screenshot collection log entries" on
- [ ] In-game setting: Collection log - New addition notification = popup
- [ ] Miscellaneous > Delay CoX popups until chest = on
- [ ] Miscellaneous > Censor CoX loot in chat = Everyone's

## Popup hold + screenshot
- [ ] `::coxsim Twisted bow` - no popup, no sound
- [ ] `::coxopen` - popup + sound. One screenshot `Collection log (Twisted bow) <date>.png` lands in `.runelite/screenshots/<name>/Collection Log/` and shows the fully opened popup
- [ ] Repeat, but open the bank instead of `::coxopen` - same result
- [ ] Repeat, but open a CoX private storage unit instead (needs to be in CoX) - same result
- [ ] Delay off, `::coxsim Twisted bow` - popup immediately. Our plugin takes no screenshot (no native popup in a simulation, so no Screenshot plugin one either)
- [ ] Screenshot plugin off, delay on, `::coxsim` + `::coxopen` - popup, no screenshot

## Chat censor
- [ ] Off: `::coxsim Twisted bow` - every line shows "Twisted bow"
- [ ] Everyone's: `::coxsim Twisted bow` - party line `<you> - ???`, clan line `...from a raid: ???`, `Valuable drop: ???` (no coin value), `New item added to your collection log: ???`
- [ ] Everyone's: `::coxsim Elder maul Some Player` - party and clan lines show `???`
- [ ] Only mine: `::coxsim Elder maul Some Player` - both lines show "Elder maul"
- [ ] Only mine: `::coxsim Twisted bow` - your lines show `???`
- [ ] `::coxopen` - all lines show the real item again, without scrolling or reopening chat
- [ ] While censored, switch the setting to Off - lines show the real item at once
- [ ] Switch to other chat tabs (Clan, Game) while censored - still censored, and restored after `::coxopen`
- [ ] Censor on, delay off - chat censored, popup shows straight away (the settings are independent)

## Interplay
- [ ] `::coxsim Twisted bow`, then `::clogtest Abyssal whip` - the whip popup shows straight away and isn't blocked by the held item
- [ ] `::coxsim Twisted bow`, `::coxsim Elder maul`, `::coxopen` - both popups in order, two screenshots
- [ ] Repeat the hold + release with each panel style: Colourful, Neutral, Audio only (Audio only: sound at release, the screenshot has no popup in it)
- [ ] A non-CoX clog line arriving while censored (`::clogtest Abyssal whip`) is not censored

## Safety
- [ ] `::coxsim Twisted bow`, log out, log back in - no censored lines left; a fresh `::coxsim` works
- [ ] `::coxsim Twisted bow`, hop worlds - same
- [ ] `::coxsim Twisted bow`, turn the plugin off - lines restored

## CoX Censor warning
- [ ] CoX Censor not installed: turning either CoX setting on gives no warning
- [ ] CoX Censor installed from the hub and enabled (real client):
  - [ ] turning the delay on posts a red warning in chat
  - [ ] chat censor Off -> Only mine posts the warning
  - [ ] Only mine -> Everyone's posts no warning
  - [ ] turning a setting off posts no warning
- [ ] CoX Censor installed but disabled: no warning

## Real raid only
These depend on the game's own popup scripts, which can't be simulated.
- [ ] At completion: the game's collection log popup doesn't show (all panel styles, Audio only too)
- [ ] At completion: chat lines are censored (party, clan, valuable drop, collection log)
- [ ] At completion: no screenshot is saved
- [ ] At the chest: popup, sound, chat restored, screenshot with the popup in it
- [ ] Teammate's purple: censored in Everyone's mode, visible in Only mine mode
- [ ] Afterwards, a normal (non-CoX) collection log slot: the game popup is hidden as usual and the Screenshot plugin's own screenshot still works
- [ ] Afterwards, a combat achievement popup still appears and screenshots normally
