package fr.Gabilou.ilesFlottantes.modeles.territories;

import java.util.Objects;
import java.util.UUID;

/** Volume de blocs inclusif, normalisé même lorsque les deux coins sont inversés. */
public record Cuboid(UUID world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Cuboid {
        Objects.requireNonNull(world);
        int x = Math.min(minX, maxX), y = Math.min(minY, maxY), z = Math.min(minZ, maxZ);
        maxX = Math.max(minX, maxX); maxY = Math.max(minY, maxY); maxZ = Math.max(minZ, maxZ);
        minX = x; minY = y; minZ = z;
    }

    public boolean contains(UUID world, int x, int y, int z) {
        return this.world.equals(world) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(Cuboid other) {
        return world.equals(other.world) && minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}
