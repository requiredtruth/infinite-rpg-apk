# Generation contract

The database, not the language model, is the authority.

## Queue order

1. Count accepted content by type.
2. Add jobs for every missing required slot.
3. Claim the highest-priority queued row in a transaction.
4. Once the required manifest is full, rotate novel jobs across all content types forever.

## Acceptance flow

1. Draft pass receives the exact schema, job objective, existing same-type keys/names, and all usable material/item keys.
2. A brace-aware parser extracts one JSON object.
3. Local schema checks normalize the identity key and reject malformed color, recipe, biome, music, or pixel structures. Item/tile textures require six colors and exactly 256 explicit pixels—no shorthand.
4. Judge pass sees the candidate, schema, and existing catalog. It must explicitly approve with score ≥ 0.72.
5. Local semantic token Jaccard similarity must remain ≤ 0.82 against every same-type record.
6. SQLite's unique `(type, identity_key)` constraint is the final race-safe check.
7. Accepted content enters the live renderer/catalog immediately and triggers an in-app preview notification. Rejected jobs retry at most three times, then remain visible as rejected.

The current draft prompt and judge prompt are stored for display, and token callbacks stream output plus live tokens/second into the AI controls while inference is still running.

This deliberately uses both model judgment and deterministic enforcement. The model cannot bypass the schema, similarity threshold, or database uniqueness constraint.
