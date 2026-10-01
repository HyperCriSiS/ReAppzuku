package com.gree1d.reappzuku.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PolicyPresetDao {
    @Query("SELECT * FROM policy_preset WHERE id = :id LIMIT 1")
    PolicyPreset getById(long id);

    @Query("SELECT * FROM policy_preset ORDER BY builtIn DESC, name COLLATE NOCASE")
    List<PolicyPreset> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(PolicyPreset preset);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAllIgnore(List<PolicyPreset> presets);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<PolicyPreset> presets);

    @Delete
    void delete(PolicyPreset preset);

    @Query("DELETE FROM policy_preset WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM policy_preset WHERE builtIn = 0")
    void deleteUserPresets();

    @Query("DELETE FROM policy_preset")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM policy_preset")
    int getCount();
}