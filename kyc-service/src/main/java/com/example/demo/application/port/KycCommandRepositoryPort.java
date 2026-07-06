package com.example.demo.application.port;


import java.util.Optional;

import com.example.demo.application.domain.user.aggregate.UserIdentity;

/**
 * <h2>[領域層 - 輸出埠] KYC 寫入端持久化契約</h2>
 */
public interface KycCommandRepositoryPort {

	Optional<UserIdentity> findById(String id);

	void save(UserIdentity identity);
}