package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AetherDragon.MOD_ID);
    public static final Map<String, RegistryObject<SoundEvent>> ALL = new LinkedHashMap<>();

    static {
        String[] ids = {
                "sword", "victorymmm", "cgd", "cgh", "cgi", "cgmd", "cgmh", "cgmi", "cgmw", "cgw",
                "mud", "muh", "mui", "nad", "nah", "naw", "sabw", "sah", "saw", "spd", "sph", "spi", "spw", "shh", "shi", "shd",
                "gad", "gah", "gai", "gaw", "drd", "drf", "drh", "dri", "drw",
                // 艾瑟龙技能音效，定义见 sounds.json
                "dragon_vocal_attack", "dragon_roar", "dragon_whoosh", "dragon_claw_hit", "dragon_ground_slam", "dragon_quake_wave",
                "dragon_breath_charge", "dragon_breath_fire", "dragon_breath_loop", "dragon_crystal_summon", "dragon_crystal_fall",
                "dragon_crystal_impact", "dragon_spike_erupt", "dragon_thunder_mark", "dragon_feather_launch", "dragon_feather_hit",
                "dragon_gale_blast", "dragon_heavy_step", "dragon_charge_impact", "dragon_tail_whip",
                // 灵魂技能音效，定义见 sounds.json
                "spirit_slash", "spirit_slash_hit", "spirit_charge", "spirit_impact", "spirit_erupt", "spirit_rune_summon",
                "spirit_rune_launch", "spirit_rune_hit", "spirit_dash", "spirit_sky_flash", "spirit_sword_fall", "spirit_sword_impact",
                "spirit_nova", "spirit_enrage",
                // 天境娜迦技能音效，定义见 sounds.json
                "naga_hiss", "naga_bite", "naga_bite_hit", "naga_tail_sweep", "naga_stone_slam", "naga_burrow", "naga_rumble",
                "naga_erupt", "naga_beam_charge", "naga_beam_fire", "naga_beam_loop", "naga_pillar", "naga_boulder_throw",
                "naga_boulder_impact", "naga_enrage",
                // 水晶石傀儡技能音效，定义见 sounds.json
                "golem_punch", "golem_punch_hit", "golem_impact", "golem_spike", "golem_shatter", "golem_step", "golem_charge",
                "golem_cage",
                // 盐蟹王技能音效，定义见 sounds.json
                "crab_snap", "crab_snap_hit", "crab_slam", "crab_scuttle", "crab_spit", "crab_bubble_pop", "crab_geyser", "crab_wave",
                "crab_salt", "crab_roar",
                // 远古潜影贝技能音效，定义见 sounds.json
                "shulker_open", "shulker_close", "shulker_fire", "shulker_orb_hit", "shulker_beam_charge", "shulker_beam_fire",
                "shulker_beam_loop", "shulker_vine", "shulker_vanish", "shulker_impact", "shulker_pulse", "shulker_burst", "shulker_enrage"
        };
        for (String id : ids) {
            ALL.put(id, SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(AetherDragon.MOD_ID, id))));
        }
    }

    // 服务端在指定位置播放本模组音效，附近玩家都能听到
    public static void play(Level level, String id, double x, double y, double z, float volume, float pitch) {
        RegistryObject<SoundEvent> sound = ALL.get(id);
        if (sound != null) {
            level.playSound(null, x, y, z, sound.get(), SoundSource.HOSTILE, volume, pitch);
        }
    }

    private ModSounds() {
    }
}
