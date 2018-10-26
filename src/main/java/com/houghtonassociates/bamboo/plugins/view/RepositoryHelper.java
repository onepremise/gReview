package com.houghtonassociates.bamboo.plugins.view;

import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.repository.Repository;
import org.jetbrains.annotations.Nullable;

public class RepositoryHelper {

    @Nullable
    public static Repository getDefaultRepository(ImmutableChain chain) {
        return chain.getPlanRepositoryDefinitions().stream()
                .findFirst()
                .map(planRepositoryDefinition -> planRepositoryDefinition.asLegacyData().getRepository())
                .orElse(null);
    }
}
