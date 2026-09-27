-- Jeu de données de démonstration pour l'utilisateur "demo" (utilisateur par défaut du front).
INSERT INTO transactions (user_id, label, amount, currency, created_at) VALUES
    ('demo', 'Salaire',                    2850.00, 'EUR', NOW() - INTERVAL '20 days'),
    ('demo', 'Loyer',                      -950.00, 'EUR', NOW() - INTERVAL '18 days'),
    ('demo', 'Courses supermarché',         -84.37, 'EUR', NOW() - INTERVAL '10 days'),
    ('demo', 'Abonnement transport',        -86.40, 'EUR', NOW() - INTERVAL '7 days'),
    ('demo', 'Restaurant avec l''équipe',    -42.50, 'EUR', NOW() - INTERVAL '3 days'),
    ('demo', 'Remboursement note de frais', 120.00, 'EUR', NOW() - INTERVAL '1 day');
