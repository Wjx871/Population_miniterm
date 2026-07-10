package com.wjx871.population.auth;

public record LoginVO(String token, String tokenType, long expiresIn, UserVO user) {
}
