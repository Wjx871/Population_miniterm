package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.wjx871.population.cache.OptionalRedisService;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class Phase11TokenRevocationTest {
 private final String secret="phase11-test-secret-must-have-at-least-thirty-two-bytes";
 @Test void jwtContainsUniqueJti(){JwtService jwt=new JwtService(secret,10);AuthenticatedUser u=user();Claims a=jwt.parseClaims(jwt.createToken(u));Claims b=jwt.parseClaims(jwt.createToken(u));assertThat(a.getId()).isNotBlank().isNotEqualTo(b.getId());}
 @Test void jwtContainsSubjectAndExpiration(){JwtService jwt=new JwtService(secret,10);Claims c=jwt.parseClaims(jwt.createToken(user()));assertThat(c.getSubject()).isEqualTo("tester");assertThat(c.getExpiration().toInstant()).isAfter(Instant.now());}
 @Test void localBlacklistWorksWhenRedisDisabled(){OptionalRedisService redis=mock(OptionalRedisService.class);when(redis.key(anyString())).thenAnswer(i->"population:"+i.getArgument(0));TokenRevocationService s=new TokenRevocationService(redis);s.revoke("jti-1",Instant.now().plusSeconds(60));assertThat(s.isRevoked("jti-1")).isTrue();}
 @Test void expiredBlacklistEntryIsIgnored(){OptionalRedisService redis=mock(OptionalRedisService.class);when(redis.key(anyString())).thenReturn("key");TokenRevocationService s=new TokenRevocationService(redis);s.revoke("old",Instant.now().minusSeconds(1));assertThat(s.isRevoked("old")).isFalse();}
 @Test void blacklistStoresOnlyJtiDerivedKey(){OptionalRedisService redis=mock(OptionalRedisService.class);when(redis.key(anyString())).thenAnswer(i->"population:"+i.getArgument(0));TokenRevocationService s=new TokenRevocationService(redis);s.revoke("safe-jti",Instant.now().plusSeconds(60));verify(redis).putMarker(eq("population:security:jwt:blacklist:safe-jti"),any());}
 @Test void unknownJtiUsesRedisFallback(){OptionalRedisService redis=mock(OptionalRedisService.class);when(redis.key(anyString())).thenReturn("blacklist:x");when(redis.contains("blacklist:x")).thenReturn(true);assertThat(new TokenRevocationService(redis).isRevoked("x")).isTrue();}
 private AuthenticatedUser user(){return new AuthenticatedUser(1L,"tester","", "Tester","ENABLED",1L,"SYSTEM_ADMIN","Admin",RoleLevel.L3,DataScope.ALL,"ENABLED",1L,"Dept","110000",List.of());}
}
