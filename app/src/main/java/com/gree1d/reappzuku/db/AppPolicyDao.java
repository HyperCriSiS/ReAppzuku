package com.gree1d.reappzuku.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppPolicyDao {
    @Query("SELECT * FROM app_policy WHERE packageName = :packageName LIMIT 1")
    AppPolicy getByPackage(String packageName);

    @Query("SELECT * FROM app_policy ORDER BY packageName COLLATE NOCASE")
    List<AppPolicy> getAll();

    @Query("SELECT * FROM app_policy WHERE strategy = :strategy ORDER BY packageName COLLATE NOCASE")
    List<AppPolicy> getByStrategy(int strategy);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AppPolicy policy);

    @Delete
    void delete(AppPolicy policy);

    @Query("DELETE FROM app_policy WHERE packageName = :packageName")
    void deleteByPackage(String packageName);

    @Query("SELECT COUNT(*) FROM app_policy")
    int getCount();
}
