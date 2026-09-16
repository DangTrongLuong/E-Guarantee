package com.example.ecommerce.repository;

import com.example.ecommerce.entity.AccessTokenBlackList;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessTokenBlackListRepository extends CrudRepository<AccessTokenBlackList, String> {
}

