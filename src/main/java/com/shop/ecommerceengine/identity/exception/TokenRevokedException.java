package com.shop.ecommerceengine.identity.exception;

/**
 * Exception thrown when a token has been revoked or blacklisted.
 * This includes replay attack detection when a refresh token is reused.
 */
public class TokenRevokedException extends AuthException {

    private final boolean replayAttack;

    public TokenRevokedException() {
        super("Token has been revoked", TOKEN_REVOKED);
        this.replayAttack = false;
    }

    public TokenRevokedException(String message) {
        super(message, TOKEN_REVOKED);
        this.replayAttack = false;
    }

    public TokenRevokedException(String message, boolean replayAttack) {
        super(message, replayAttack ? REPLAY_ATTACK : TOKEN_REVOKED);
        this.replayAttack = replayAttack;
    }

    public boolean isReplayAttack() {
        return replayAttack;
    }

    public static TokenRevokedException replayAttackDetected() {
        return new TokenRevokedException(
                "Refresh token reuse detected - possible replay attack. All tokens revoked.",
                true
        );
    }
}
