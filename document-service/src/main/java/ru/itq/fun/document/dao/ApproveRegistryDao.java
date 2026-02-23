package ru.itq.fun.document.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.itq.fun.document.entity.ApproveRegistry;

@Repository
public interface ApproveRegistryDao extends JpaRepository<ApproveRegistry, Long> {


}
