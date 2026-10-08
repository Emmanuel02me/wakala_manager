package com.Wakala.v1.Service;

import com.Wakala.v1.Dto.NetworkRateResponse;
import com.Wakala.v1.Entity.NetworkRate;
import com.Wakala.v1.Entity.Transaction;
import com.Wakala.v1.Exception.BusinessException;
import com.Wakala.v1.Repositories.NetworkRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NetworkRateService {

    private final NetworkRateRepository networkRateRepository;

    @Transactional(readOnly = true)
    public NetworkRate findApplicableRate(Long providerId,
            Transaction.TransactionType type,
            BigDecimal amount) {
        return networkRateRepository.findApplicable(providerId, type, amount, LocalDate.now())
                .orElseThrow(() -> new BusinessException(
                        "Hakuna network rate inayolingana na kiasi " + amount +
                                " kwa provider " + providerId + " na aina " + type));
    }

    @Transactional(readOnly = true)
    public List<NetworkRateResponse> getAllActive() {
        return networkRateRepository.findByActiveTrueOrderByProviderIdAscTransactionTypeAscMinAmountAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<NetworkRateResponse> getByProviderAndType(Long providerId,
            Transaction.TransactionType type) {
        return networkRateRepository
                .findByProviderIdAndTransactionTypeAndActiveTrueOrderByMinAmountAsc(providerId, type)
                .stream().map(this::toResponse).toList();
    }

    private NetworkRateResponse toResponse(NetworkRate r) {
        LocalDate today = LocalDate.now();
        return new NetworkRateResponse(
                r.getId(),
                r.getProvider().getId(),
                r.getProvider().getName(),
                r.getTransactionType().name(),
                r.getMinAmount(),
                r.getMaxAmount(),
                r.getNetworkCommission(),
                r.getEffectiveFrom(),
                r.getEffectiveTo(),
                r.isActive(),
                r.isEffectiveOn(today),
                r.getNotes());
    }
}