package oborokage.check;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import oborokage.Oborokage;

/**
 * 1.0.2: the dojo's structure set loads with the new spacing (10 chunks, separation 4) and still
 * holds the dojo; the tomb's set is unchanged (36, 12). How often dojos then appear in cherry
 * groves is measured by the survey (--only=survey on a normal world).
 */
final class DensityChecks {
  private final ServerChecks t;

  DensityChecks(ServerChecks t) {
    this.t = t;
  }

  boolean tick() {
    var sets = t.level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
    var dojo = sets.getValue(Oborokage.id("dojo"));
    var tomb = sets.getValue(Oborokage.id("blade_tomb"));
    var d = (RandomSpreadStructurePlacement) dojo.placement();
    var b = (RandomSpreadStructurePlacement) tomb.placement();
    var structures = t.level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    boolean holds =
        dojo.structures().stream()
            .anyMatch(e -> e.structure().value() == structures.getValue(Oborokage.id("dojo")));
    t.check(
        d.spacing() == 10 && d.separation() == 4 && holds,
        "The dojo's structure set loads with spacing 10 / separation 4: "
            + d.spacing()
            + " / "
            + d.separation());
    t.check(
        b.spacing() == 36 && b.separation() == 12,
        "The tomb's structure set is unchanged: " + b.spacing() + " / " + b.separation());
    return true;
  }
}
