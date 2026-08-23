# Collection Log Popup Enhanced
Displays a popup and (optional) sound effect based on rarity on new collection logs.

# Setup
In settings menu, 
1. enable 'Collection log - New addition notification'
2. enable 'Collection log - New addition popup'
![alt text](clogSettings.png)

# Default visuals
![img_1.png](img_1.png)
![alt text](image-2.png)
![alt text](image.png)
![alt text](image-4.png)
![alt text](image-3.png)

# Testing
To test the popup without having to unlock a new collection log, select any tier in the 'preview popup' dropdown. This will show the popup as if you got a random item from that tier.

(Don't forget to turn it off again when you're done testing)

![alt text](testing_settings.png)

# Rarity tiers
The popup has 5 different rarity tiers:
* Common
* Uncommon
* Rare
* Very rare
* Pet

Each has a different popup style and color scheme. You can customize rarity tier cutoffs based on:
* value
* wiki% (https://oldschool.runescape.wiki/w/Collection_log/Table)
* combination of both

![alt text](raritytiers_settings.png)

# Appearance

### Scaling
You can also scale the popup and set text-rendering to what looks good to you. Crisp text looks best at higher scale, smooth at lower.

Keep in mind that the popup might not cover the build-in popup on when set too small. (Build-in needs to be active if you wish screenshots to wait for the popup to be fully open)

![alt text](appearances_settings.png)

### Colors
Each tier has it's own color, which you can customize in the color section of the config, alognside text colors.

![alt text](color_settings.png)

# Statistics
At the bottom left and right of the panel statistics are displayed (configurable in setting). These values can be :
- KC at which you got the item 
-- if no KC is available, it falls back to showing comp%
- Completion 
-- based on how many other players have this item (https://oldschool.runescape.wiki/w/Collection_log/Table)
- Drop rate
-- Shows drop rate of the item if available. Falls back to value if not.
- Value of the item
-- G.E or High alch price.

![alt text](statistics_settings.png)

# Custom sounds
Want to use your own sound effects instead? Place any or all '.wav' files into '.runelite/collection-log-popup-enhanced/sounds/', using these exact filenames:
- `common.wav`
- `uncommon.wav`
- `rare.wav`
- `very_rare.wav`
- `pet.wav`

And it will use your sound instead! (See 'Testing' on how to test if your audio works)
(Note: if it doesn't play a sound and the spelling is correct, your audio file is not supported, try another.)

# Thanks to:
C engineer plugin for collection log slot popup tie ins.
Trailblazer audio effect for references to scripts on playing audio

The popup backgrounds are from:
https://bdragon1727.itch.io/custom-border-and-panels-menu-all-part
and customized by me in photoshop.

Sound effects:
Common: https://freesound.org/people/Kenneth_Cooney/sounds/609336/

Uncommon: https://freesound.org/people/PearceWilsonKing/sounds/238855/

Rare: https://freesound.org/people/jobro/sounds/198808/

Very rare: https://freesound.org/people/_MC5_/sounds/524848/

Pet: https://freesound.org/people/Tuudurt/sounds/275104/
