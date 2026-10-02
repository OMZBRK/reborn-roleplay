-- Monnaie premium : « ZK Coin » (résidu Zenkai) devient « RBCoins » (ADR 0006).
-- Renommage simple : aucune donnée perdue.
ALTER TABLE "User" RENAME COLUMN "zkCoinBalance" TO "rbCoinBalance";
