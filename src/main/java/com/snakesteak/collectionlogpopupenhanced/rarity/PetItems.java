package com.snakesteak.collectionlogpopupenhanced.rarity;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.runelite.api.gameval.ItemID;

/**
 * Maps a collection log pet's display name (as it appears in the "New item added to your
 * collection log" chat message) to its item id.
 *
 * Pets never enter the player's inventory or land on the ground when unlocked - they attach
 * directly as a follower NPC - so {@link ItemIdResolver}'s inventory/ground/GE-search pipeline can
 * never find them, and most are untradeable anyway so the GE fallback wouldn't help either. Their
 * ids are looked up here instead, by name.
 *
 * Some pets have several item ids (recolours, alternate poses, or a variant per skilling
 * material) - only one representative id is kept per pet, since it's only used to render an icon.
 */
final class PetItems
{
	private static final Map<String, Integer> ITEM_ID_BY_NAME = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	static
	{
		ITEM_ID_BY_NAME.put("Pet chaos elemental", ItemID.CHAOSELEPET);
		ITEM_ID_BY_NAME.put("Pet dagannoth supreme", ItemID.SUPREMEPET);
		ITEM_ID_BY_NAME.put("Pet dagannoth prime", ItemID.PRIMEPET);
		ITEM_ID_BY_NAME.put("Pet dagannoth rex", ItemID.REXPET);
		ITEM_ID_BY_NAME.put("Pet penance queen", ItemID.PENANCEPET);
		ITEM_ID_BY_NAME.put("Pet kree'arra", ItemID.ARMADYLPET);
		ITEM_ID_BY_NAME.put("Pet general graardor", ItemID.BANDOSPET);
		ITEM_ID_BY_NAME.put("Pet zilyana", ItemID.SARADOMINPET);
		ITEM_ID_BY_NAME.put("Pet k'ril tsutsaroth", ItemID.ZAMORAKPET);
		ITEM_ID_BY_NAME.put("Baby mole", ItemID.MOLEPET);
		ITEM_ID_BY_NAME.put("Prince black dragon", ItemID.KBDPET);
		ITEM_ID_BY_NAME.put("Kalphite princess", ItemID.KQPET_WALKING);
		ITEM_ID_BY_NAME.put("Pet smoke devil", ItemID.SMOKEPET);
		ITEM_ID_BY_NAME.put("Pet kraken", ItemID.KRAKENPET);
		ITEM_ID_BY_NAME.put("Pet corporeal critter", ItemID.CORPPET);
		ITEM_ID_BY_NAME.put("Pet dark core", ItemID.COREPET);
		ITEM_ID_BY_NAME.put("Pet snakeling", ItemID.SNAKEPET);
		ITEM_ID_BY_NAME.put("Chompy chick", ItemID.CHOMPYBIRD_PET);
		ITEM_ID_BY_NAME.put("Venenatis spiderling", ItemID.VENENATIS_PET);
		ITEM_ID_BY_NAME.put("Callisto cub", ItemID.CALLISTO_PET);
		ITEM_ID_BY_NAME.put("Vet'ion jr.", ItemID.VETION_PET);
		ITEM_ID_BY_NAME.put("Scorpia's offspring", ItemID.SCORPIA_PET);
		ITEM_ID_BY_NAME.put("Tzrek-jad", ItemID.JAD_PET);
		ITEM_ID_BY_NAME.put("Hellpuppy", ItemID.HELL_PET);
		ITEM_ID_BY_NAME.put("Abyssal orphan", ItemID.ABYSSALSIRE_PET);
		ITEM_ID_BY_NAME.put("Tektiny", ItemID.TEKTONPET);
		ITEM_ID_BY_NAME.put("Vasa minirio", ItemID.VASAPET);
		ITEM_ID_BY_NAME.put("Vespina", ItemID.VESPULAPET);
		ITEM_ID_BY_NAME.put("Heron", ItemID.SKILLPETFISH);
		ITEM_ID_BY_NAME.put("Rock golem", ItemID.SKILLPETMINING);
		ITEM_ID_BY_NAME.put("Beaver", ItemID.SKILLPETWC);
		ITEM_ID_BY_NAME.put("Baby chinchompa", ItemID.SKILLPETHUNTER_RED);
		ITEM_ID_BY_NAME.put("Bloodhound", ItemID.BLOODHOUND_PET);
		ITEM_ID_BY_NAME.put("Giant squirrel", ItemID.SKILLPETAGILITY);
		ITEM_ID_BY_NAME.put("Tangleroot", ItemID.SKILLPETFARMING);
		ITEM_ID_BY_NAME.put("Rift guardian", ItemID.SKILLPETRUNECRAFTING_AIR);
		ITEM_ID_BY_NAME.put("Rocky", ItemID.SKILLPETTHIEVING);
		ITEM_ID_BY_NAME.put("Phoenix", ItemID.PHOENIXPET);
		ITEM_ID_BY_NAME.put("Olmlet", ItemID.OLMPET);
		ITEM_ID_BY_NAME.put("Skotos", ItemID.SKOTIZOPET);
		ITEM_ID_BY_NAME.put("Jal-nib-rek", ItemID.INFERNOPET);
		ITEM_ID_BY_NAME.put("Herbi", ItemID.HERBIBOARPET);
		ITEM_ID_BY_NAME.put("Noon", ItemID.DAWNPET);
		ITEM_ID_BY_NAME.put("Vorki", ItemID.VORKATHPET);
		ITEM_ID_BY_NAME.put("Lil' zik", ItemID.VERZIKPET);
		ITEM_ID_BY_NAME.put("Ikkle hydra", ItemID.HYDRAPET);
		ITEM_ID_BY_NAME.put("Sraracha", ItemID.SARACHNISPET);
		ITEM_ID_BY_NAME.put("Youngllef", ItemID.GAUNTLETPET);
		ITEM_ID_BY_NAME.put("Smolcano", ItemID.ZALCANOPET);
		ITEM_ID_BY_NAME.put("Little nightmare", ItemID.NIGHTMAREPET);
		ITEM_ID_BY_NAME.put("Lil' creator", ItemID.SOULWARSPET_BLUE);
		ITEM_ID_BY_NAME.put("Tiny tempor", ItemID.TEMPOROSSPET);
		ITEM_ID_BY_NAME.put("Nexling", ItemID.NEXPET);
		ITEM_ID_BY_NAME.put("Abyssal protector", ItemID.ABYSSALPET);
		ITEM_ID_BY_NAME.put("Tumeken's guardian", ItemID.WARDENPET_TUMEKEN);
		ITEM_ID_BY_NAME.put("Muphin", ItemID.MUSPAHPET);
		ITEM_ID_BY_NAME.put("Wisp", ItemID.WHISPERERPET);
		ITEM_ID_BY_NAME.put("Butch", ItemID.VARDORVISPET);
		ITEM_ID_BY_NAME.put("Lil'viathan", ItemID.LEVIATHANPET);
		ITEM_ID_BY_NAME.put("Baron", ItemID.DUKESUCELLUSPET);
		ITEM_ID_BY_NAME.put("Scurry", ItemID.SCURRIUSPET);
		ITEM_ID_BY_NAME.put("Smol heredit", ItemID.SOLHEREDITPET);
		ITEM_ID_BY_NAME.put("Quetzin", ItemID.QUETZALPET);
		ITEM_ID_BY_NAME.put("Nid", ItemID.ARAXXORPET);
		ITEM_ID_BY_NAME.put("Huberte", ItemID.HUEYPET);
		ITEM_ID_BY_NAME.put("Moxi", ItemID.AMOXLIATLPET);
		ITEM_ID_BY_NAME.put("Bran", ItemID.RTBRANDAPET);
		ITEM_ID_BY_NAME.put("Yami", ItemID.YAMAPET);
		ITEM_ID_BY_NAME.put("Dom", ItemID.DOMPET);
		ITEM_ID_BY_NAME.put("Soup", ItemID.SKILLPETSAILING);
		ITEM_ID_BY_NAME.put("Gull", ItemID.GRYPHONBOSSPET);
		ITEM_ID_BY_NAME.put("Beef", ItemID.COWBOSSPET);
		ITEM_ID_BY_NAME.put("Maggot marquess", ItemID.MAGGOTKINGPET);
	}

	private PetItems()
	{
	}

	/**
	 * @return the pet's item id, or {@code null} if {@code name} isn't a known pet
	 */
	static Integer idForName(String name)
	{
		return ITEM_ID_BY_NAME.get(name);
	}

	static Set<String> names()
	{
		return ITEM_ID_BY_NAME.keySet();
	}
}
