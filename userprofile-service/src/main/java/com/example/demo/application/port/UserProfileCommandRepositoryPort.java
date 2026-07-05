package com.example.demo.application.port;


import java.util.Optional;

import com.example.demo.application.domain.user.aggregate.UserProfile;

/**
 * <h2>[領域層 - 輸出埠] 個人檔案寫入端持久化契約</h2>
 */
public interface UserProfileCommandRepositoryPort {

    Optional<UserProfile> findById(String id);

    void save(UserProfile profile);
}