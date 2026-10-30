package fr.Gabilou.ilesFlottantes.modeles.territories;

import java.util.Objects;

/** Zone nommée appartenant à une équipe ; plusieurs zones peuvent partager le même propriétaire. */
public record Region(String id, String team, Cuboid bounds) {
    public Region {
        if (id == null || !id.matches("[a-z0-9_-]{1,40}") || team == null || team.isBlank()) throw new IllegalArgumentException();
        Objects.requireNonNull(bounds);
    }
}
