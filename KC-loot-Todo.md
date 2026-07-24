This document contains all the unique mechanics or collection log dropping monsters that still need to be implemented. Both for the list of droprates for unique items from monsters and how to track KC.

# Not implementing KC checks (at this moment)
* Fight caves and Inferno. The pet popup message should take care of the pet and the single other item is a guaranteed drop so KC doesnt matter.
* Raids (calculating luck for scaling encounters can be difficult, might revisit in the future)
* Any minigame that uses searches (see chapter below) as we cannot reliably check the count
* Skilling pets, dont have KC (we could show xp and or level in the future)
* Chompy bird hunting already uses its own KC system for the rewards so not needed
* All non-boss monsters (too complicated to track or not reliable)

# Clue scrolls
Clue scrolls (caskets), should just work as they appear in inventory (or ground) and unlock collection log. A 'You have completed XX *type* Treasure Trails.' message appears before the clog so we could count that as 'KC'.

# KC and loot seperated
Some content shows a killcount, but the actuall loot (and thus unlocking clogs) happens later, by opening a chest or searching something for permits. We need to save the latest known KC so we can pull this up when the actual clog is made.

# Searches
The minigames Temporos, Wintertodt and Guardians of the rift, include a 'search' mechanic to gain loot, each kill gives a certain number of points to search for loot based on performance. This is of course different from KC. There isn't a clear indicator to know how many searches you've done so we dont include KC for these.

# The Gauntlet
Has a completion message after completing gauntlet ('You gauntlet completion count is X.) but the actual collection log items are unlocked upon opening the chest. Maybe we can use https://runelite.net/plugin-hub/show/gauntlet-chest-popup to check for items after opening the chest and unlocking clogs. We do need the KC from completing the boss though.

# Minigames
Note, any minigame that uses currency to unlock items do not count KC.

# TzHaar / Slayer monsters / Miscellaneous / Revenants
Decided against - see "Not implementing KC checks" above.