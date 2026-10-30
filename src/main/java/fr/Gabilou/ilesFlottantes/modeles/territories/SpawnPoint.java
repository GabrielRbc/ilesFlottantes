package fr.Gabilou.ilesFlottantes.modeles.territories;

import java.util.Objects;
import java.util.UUID;

/** Position persistante d’un spawn, avec orientation et identité du monde, indépendante de Bukkit. */
public record SpawnPoint(UUID world, double x, double y, double z, float yaw, float pitch) {
    public SpawnPoint {
        Objects.requireNonNull(world);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
            throw new IllegalArgumentException();
        }
    }
}
