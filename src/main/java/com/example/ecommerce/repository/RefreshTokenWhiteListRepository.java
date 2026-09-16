package com.example.ecommerce.repository;

import com.example.ecommerce.entity.AccessTokenBlackList;
import com.example.ecommerce.entity.RefreshTokenWhiteList;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface RefreshTokenWhiteListRepository extends CrudRepository<RefreshTokenWhiteList, String> {
}

