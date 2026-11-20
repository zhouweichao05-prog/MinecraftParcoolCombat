package com.MrGhost.parkourCombat.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatTargetDamageObservationTest {
    @Test
    void retainsBoundedObservationsAndMarksTruncation() {
        UUID target = UUID.randomUUID();
        List<CombatDamageEventObservation> observations = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            observations.add(new CombatDamageEventObservation(
                    CombatDamageEventType.LIVING_DAMAGE, i + 1.0F, false, true,
                    CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK, i));
        }
        CombatTargetDamageObservation observation =
                new CombatTargetDamageObservation(target, observations, 12, true);

        assertEquals(target, observation.targetUuid());
        assertEquals(8, observation.observations().size());
        assertEquals(12, observation.totalEventCount());
        assertTrue(observation.truncated());
        assertTrue(observation.positiveObserved());
        assertFalse(observation.canceledObserved());
        assertTrue(observation.ambiguousSourceObserved());
        assertThrows(UnsupportedOperationException.class,
                () -> observation.observations().clear());
    }

    @Test
    void canceledAndAmbiguousAreDerivedPerEvent() {
        UUID target = UUID.randomUUID();
        List<CombatDamageEventObservation> observations = List.of(
                new CombatDamageEventObservation(
                        CombatDamageEventType.LIVING_HURT, 2.0F, true, false,
                        CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK, 1),
                new CombatDamageEventObservation(
                        CombatDamageEventType.LIVING_DAMAGE, 0.0F, false, false,
                        CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK, 2)
        );
        CombatTargetDamageObservation observation =
                new CombatTargetDamageObservation(target, observations, 2, false);

        assertTrue(observation.canceledObserved());
        assertTrue(observation.ambiguousSourceObserved());
        assertFalse(observation.positiveObserved());
        assertFalse(observation.truncated());
    }
}
