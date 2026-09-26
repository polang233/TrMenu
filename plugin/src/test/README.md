# Player data deletion regression (#274)

Run the storage ordering tests with `./gradlew :plugin:test` (Java 8, as in CI).
These tests cover a delayed write followed by deletion, an ordered read, setting a
new value after deletion, draining writes on close, and continuing after a failed
write. They do not start Bukkit or connect to a database.

## Server checks

Copy `resources/metadata-delete.yml` into the test server's TrMenu menus directory.
Use two test players, each with a `playerData` value. Run the following checks with
`Database.Use-Legacy-Database: false` on SQLite and MySQL:

1. Left-click, then right-click: the placeholder becomes null and the current
   player's `(user UUID, key = playerData)` database row is deleted. The second
   player's row remains unchanged.
2. Reconnect and fully restart the server: the deleted value does not return.
3. Shift-left-click: `playerData` and `playerDataExtra` are deleted; `keepData`
   remains. Repeat the deletion to check that it is harmless when no keys match.
4. Shift-right-click: the final value is `new`, including after reconnect/restart.
   Also repeat left/right clicks quickly and stop the server after the final
   right-click: pending writes must not restore the removed row.
5. Exercise Kether `data set` / `data del` and the player DATA command's REMOVE
   operation: they must have the same persistence and missing-key behavior.
6. Repeat menu set/delete/reconnect/restart checks using the legacy database mode;
   its existing periodic/quit save behavior should still persist deletion.

Deletion uses the existing DAO and table. It needs no schema migration. Explicit
empty-string values retain the existing storage/loading behavior; null represents
deletion. Cross-server write coordination is outside these local ordering tests.
