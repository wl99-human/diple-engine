package com.google.payments.diple.repository;

import com.google.payments.diple.domain.JournalPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JournalPostingRepository extends JpaRepository<JournalPosting, Long> {

    List<JournalPosting> findByTransactionIdOrderByEntrySequenceAsc(String transactionId);

    List<JournalPosting> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<JournalPosting> findTop50ByOrderByCreatedAtDescIdDesc();

    @Query("SELECT j FROM JournalPosting j WHERE j.accountId = :accountId ORDER BY j.createdAt DESC, j.id DESC LIMIT 1")
    Optional<JournalPosting> findLatestPostingForAccount(@Param("accountId") String accountId);

    @Query("SELECT j FROM JournalPosting j ORDER BY j.id ASC")
    List<JournalPosting> findAllOrderByIdAsc();
}
