package oborokage.check;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import oborokage.Oborokage;

/**
 * The dojos' structure set loads with its spacing (1.1.0: 8 chunks, separation 4, the dojo and the
 * ruined dojo three to one; 1.0.2 had 10 / 4 for the dojo alone) and the tomb's set is unchanged
 * (36, 12). How often dojos then appear in cherry groves is measured by the survey (--only=survey
 * on a normal world).
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
        d.spacing() == StructureChecks.SPACING
            && d.separation() == StructureChecks.SEPARATION
            && holds,
        "The dojo's structure set loads with spacing 8 / separation 4: "
            + d.spacing()
            + " / "
            + d.separation());
    t.check(
        b.spacing() == 36 && b.separation() == 12,
        "The tomb's structure set is unchanged: " + b.spacing() + " / " + b.separation());
    return true;
  }
}
