package dev.emi.trinkets.data;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.trinkets.TrinketsMain;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.TrinketEnums.DropRule;
import dev.emi.trinkets.data.SlotLoader.GroupData;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloadListener;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;

public class SlotLoader extends
		SinglePreparationResourceReloadListener<Map<String, GroupData>> implements
		IdentifiableResourceReloadListener {

	public static final SlotLoader INSTANCE = new SlotLoader();

	static final Identifier ID = new Identifier(TrinketsMain.MOD_ID, "slots");

	private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping()
			.create();
	private static final int FILE_SUFFIX_LENGTH = ".json".length();

	private Map<String, GroupData> slots = new HashMap<>();

	@Override
	protected Map<String, GroupData> prepare(ResourceManager resourceManager, Profiler profiler) {
		Map<String, GroupData> map = new HashMap<>();
		String dataType = "slots";

		for (Identifier identifier : resourceManager
				.findResources(dataType, (stringx) -> stringx.endsWith(".json"))) {
			try {
				InputStreamReader reader = new InputStreamReader(
						resourceManager.getResource(identifier).getInputStream());
				JsonObject jsonObject = JsonHelper.deserialize(GSON, reader, JsonObject.class);

				if (jsonObject != null) {
					String path = identifier.getPath();
					String[] parsed = path
							.substring(dataType.length() + 1, path.length() - FILE_SUFFIX_LENGTH).split("/", 2);
					String groupName = parsed[0];
					String fileName = parsed[1];
					GroupData group = map.computeIfAbsent(groupName, (k) -> new GroupData());

					if (fileName.equals("group")) {
						group.read(jsonObject);
					} else {
						SlotData slot = group.slots.computeIfAbsent(fileName, (k) -> new SlotData());
						slot.read(jsonObject);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return map;
	}

	@Override
	protected void apply(Map<String, GroupData> loader, ResourceManager manager, Profiler profiler) {
		this.slots = loader;
	}

	public Map<String, GroupData> getSlots() {
		return ImmutableMap.copyOf(this.slots);
	}

	@Override
	public Identifier getFabricId() {
		return ID;
	}

	static class GroupData {

		private String defaultSlot = "";
		private Map<String, SlotData> slots = new HashMap<>();

		void read(JsonObject jsonObject) {
			defaultSlot = JsonHelper.getString(jsonObject, "default_slot", defaultSlot);
		}

		String getDefaultSlot() {
			return defaultSlot;
		}

		SlotData getSlot(String name) {
			return slots.get(name);
		}
	}

	static class SlotData {

		private int order = 0;
		private int amount = 1;
		private int locked = 0;
		private String icon = "";
		private boolean transferable = false;
		private Set<String> validators = new HashSet<>();
		private String dropRule = DropRule.DEFAULT.toString();

		SlotType create(String name) {
			Identifier finalIcon = new Identifier(icon);
			Set<Identifier> finalValidators = validators.stream().map(Identifier::new)
					.collect(Collectors.toSet());
			return new SlotType(name, order, amount, locked, finalIcon, transferable, finalValidators,
					DropRule.valueOf(dropRule));
		}

		void read(JsonObject jsonObject) {
			boolean replace = JsonHelper.getBoolean(jsonObject, "replace", false);

			int jsonOrder = JsonHelper.getInt(jsonObject, "order", order);
			order = replace ? jsonOrder : Math.max(jsonOrder, order);

			int jsonAmount = JsonHelper.getInt(jsonObject, "amount", amount);
			amount = replace ? jsonAmount : Math.max(jsonAmount, amount);

			int jsonLocked = JsonHelper.getInt(jsonObject, "locked", locked);
			locked = replace ? jsonLocked : Math.max(jsonLocked, locked);

			icon = JsonHelper.getString(jsonObject, "icon", icon);

			boolean jsonTransferable = JsonHelper.getBoolean(jsonObject, "transferable", transferable);
			transferable = replace ? jsonTransferable : (transferable || jsonTransferable);

			String jsonDropRule = JsonHelper.getString(jsonObject, "drop_rule", dropRule);

			if (DropRule.has(jsonDropRule)) {
				dropRule = jsonDropRule;
			}
			JsonArray jsonValidators = JsonHelper.getArray(jsonObject, "validators", new JsonArray());

			if (jsonValidators != null) {

				if (replace && jsonValidators.size() > 0) {
					validators.clear();
				}

				for (JsonElement jsonValidator : jsonValidators) {
					validators.add(jsonValidator.getAsString());
				}
			}
		}
	}
}