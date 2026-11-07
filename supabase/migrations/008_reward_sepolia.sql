-- Allow Ethereum Sepolia (11155111) as a StudyCoin test network, next to Polygon Amoy and the local EVM.
-- Additive: existing config rows keep their network. Run after 007.
begin;
alter table public.studymind_reward_config drop constraint if exists studymind_reward_config_chain_id_check;
alter table public.studymind_reward_config
  add constraint studymind_reward_config_chain_id_check check (chain_id in (80002,11155111,31337));
commit;
