// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {AccessControlDefaultAdminRules} from "@openzeppelin/contracts/access/extensions/AccessControlDefaultAdminRules.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";

/// @notice Managed learning rewards. Transfers/allowances are deliberately disabled.
/// Authenticated StudyMind requests authorize the operator to mint/redeem for custodial wallets.
contract StudyCoin is ERC20, AccessControlDefaultAdminRules, Pausable {
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    uint256 public constant LIFETIME_CAP = 1_000_000_000;
    uint256 public lifetimeMinted;

    struct Operation { address wallet; uint256 amount; uint32 daysGranted; uint8 kind; }
    mapping(bytes32 => Operation) public operations;
    event RewardOperation(bytes32 indexed operationId, address indexed wallet, uint8 kind, uint256 amount, uint32 daysGranted);
    error NonTransferable();
    error InvalidOperation();
    error OperationAlreadyProcessed();
    error IssuanceCapExceeded();

    constructor(address admin, address operator)
        ERC20("StudyCoin", "SC") AccessControlDefaultAdminRules(2 days, admin) {
        if (operator == address(0)) revert InvalidOperation();
        _grantRole(OPERATOR_ROLE, operator);
    }

    function decimals() public pure override returns (uint8) { return 0; }

    function reward(bytes32 id, address wallet, uint256 amount) external onlyRole(OPERATOR_ROLE) whenNotPaused {
        _check(id, wallet, amount);
        if (amount > 1_000) revert InvalidOperation();
        if (lifetimeMinted + amount > LIFETIME_CAP) revert IssuanceCapExceeded();
        lifetimeMinted += amount;
        operations[id] = Operation(wallet, amount, 0, 1);
        _mint(wallet, amount);
        emit RewardOperation(id, wallet, 1, amount, 0);
    }

    function redeem(bytes32 id, address wallet, uint256 amount, uint32 daysGranted)
        external onlyRole(OPERATOR_ROLE) whenNotPaused {
        _check(id, wallet, amount);
        if (daysGranted == 0 || daysGranted > 365) revert InvalidOperation();
        operations[id] = Operation(wallet, amount, daysGranted, 2);
        _burn(wallet, amount);
        emit RewardOperation(id, wallet, 2, amount, daysGranted);
    }

    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) { _pause(); }
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) { _unpause(); }

    function _check(bytes32 id, address wallet, uint256 amount) private view {
        if (id == bytes32(0) || wallet == address(0) || amount == 0 || amount > 10_000)
            revert InvalidOperation();
        if (operations[id].kind != 0) revert OperationAlreadyProcessed();
    }

    function _update(address from, address to, uint256 amount) internal override {
        if (from != address(0) && to != address(0)) revert NonTransferable();
        super._update(from, to, amount);
    }

    function _approve(address, address, uint256, bool) internal pure override { revert NonTransferable(); }
}
