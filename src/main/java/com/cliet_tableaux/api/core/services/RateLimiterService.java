package com.cliet_tableaux.api.core.services;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

// Anti-spam basique en mémoire : limite le nombre d'appels autorisés pour une clé donnée
// (IP, email...) sur une fenêtre glissante. Volontairement simple pour ne pas ajouter de
// dépendance (type Bucket4j) ni d'infrastructure externe (Redis) pour ce seul besoin.
// Limite connue : l'état n'est pas partagé entre plusieurs instances de l'application et est
// perdu au redémarrage. À remplacer par une solution partagée (Bucket4j + Redis, ou équivalent)
// si l'API est un jour déployée sur plusieurs instances ou si le besoin anti-spam se généralise
// à d'autres routes.
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, Deque<Instant>> callsByKey = new ConcurrentHashMap<>();

    public boolean isAllowed(String key, int maxCalls, Duration window) {
        Deque<Instant> calls = callsByKey.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        Instant windowStart = now.minus(window);

        synchronized (calls) {
            while (!calls.isEmpty() && calls.peekFirst().isBefore(windowStart)) {
                calls.pollFirst();
            }
            if (calls.size() >= maxCalls) {
                return false;
            }
            calls.addLast(now);
            return true;
        }
    }
}
