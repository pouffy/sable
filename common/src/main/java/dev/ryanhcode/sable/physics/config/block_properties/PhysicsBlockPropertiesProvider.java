package dev.ryanhcode.sable.physics.config.block_properties;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class PhysicsBlockPropertiesProvider implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final CompletableFuture<HolderLookup.Provider> registries;
    protected final String modId;
    private final Map<ResourceLocation, PhysicsBlockPropertiesDefinition> definitions = new HashMap<>();

    public PhysicsBlockPropertiesProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries, String modId) {
        this.pathProvider = packOutput.createPathProvider(PackOutput.Target.DATA_PACK, "physics_block_properties");
        this.registries = registries;
        this.modId = modId;
    }

    protected abstract void addProperties(HolderLookup.Provider holderLookup);

    public final CompletableFuture<?> run(CachedOutput output) {
        return this.registries.thenCompose((provider) -> {
            Set<CompletableFuture<?>> list = new HashSet<>();
            this.addProperties(provider);
            this.definitions.forEach((id, definition) -> {
                list.add(DataProvider.saveStable(output, provider, PhysicsBlockPropertiesDefinition.CODEC, definition, this.pathProvider.json(id)));
            });
            return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
        });
    }

    public final PhysicsBlockPropertiesDefinition defineTag(final ResourceLocation id, final Function<Builder, Builder> builderFunction) {
        return this.definitions.computeIfAbsent(id, (k) -> builderFunction.apply(new Builder(k, true)).build());
    }

    public final PhysicsBlockPropertiesDefinition define(final ResourceLocation id, final Function<Builder, Builder> builderFunction) {
        return this.definitions.computeIfAbsent(id, (k) -> builderFunction.apply(new Builder(k, false)).build());
    }

    @Override
    public @NotNull String getName() {
        return "Block Physics Properties for " + this.modId;
    }

    public static class Builder {
        final ExtraCodecs.TagOrElementLocation selector;
        int priority = 1000;
        Map<ResourceLocation, Object> properties = new HashMap<>();
        Map<BlockStateConditionSet, Map<ResourceLocation, Object>> overrides = null;

        public Builder(ResourceLocation id, boolean tag) {
            this.selector = new ExtraCodecs.TagOrElementLocation(id, tag);
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder properties(Function<PropertyBuilder, PropertyBuilder> properties) {
            this.properties.putAll(properties.apply(new PropertyBuilder()).getProperties());
            return this;
        }

        public Builder override(BlockStateConditionSet conditions, Function<PropertyBuilder, PropertyBuilder> properties) {
            if (this.overrides == null) this.overrides = new HashMap<>();
            this.overrides.put(conditions, properties.apply(new PropertyBuilder()).getProperties());
            return this;
        }

        public PhysicsBlockPropertiesDefinition build() {
            return new PhysicsBlockPropertiesDefinition(this.selector, this.priority, this.properties, Optional.ofNullable(this.overrides));
        }

        public static class PropertyBuilder {
            Map<ResourceLocation, Object> properties = new HashMap<>();

            public <T> PropertyBuilder property(PhysicsBlockPropertyTypes.PhysicsBlockPropertyType<T> type, T value) {
                this.properties.put(PhysicsBlockPropertyTypes.REGISTRY.getKey(type), value);
                return this;
            }

            public Map<ResourceLocation, Object> getProperties() {
                return this.properties;
            }
        }
    }
}
