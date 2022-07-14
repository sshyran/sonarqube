/*
 * SonarQube
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.issue.ws;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchDto;
import org.sonar.db.issue.IssueQueryParams;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.issue.TaintChecker;
import org.sonar.server.issue.ws.pull.PullActionProtobufObjectGenerator;
import org.sonar.server.user.UserSession;

import static java.util.Optional.ofNullable;
import static org.sonarqube.ws.WsUtils.checkArgument;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.ACTION_PULL;

public class PullAction extends BasePullAction {
  private static final String ISSUE_TYPE = "issues";
  private static final String REPOSITORY_EXAMPLE = "java";
  private static final String RESOURCE_EXAMPLE = "pull-example.proto";
  private static final String SINCE_VERSION = "9.5";

  private final DbClient dbClient;

  public PullAction(System2 system2, ComponentFinder componentFinder, DbClient dbClient, UserSession userSession,
    PullActionProtobufObjectGenerator protobufObjectGenerator) {
    super(system2, componentFinder, dbClient, userSession, protobufObjectGenerator, ACTION_PULL,
      ISSUE_TYPE, REPOSITORY_EXAMPLE, SINCE_VERSION, RESOURCE_EXAMPLE);
    this.dbClient = dbClient;
  }

  @Override
  protected Set<String> getIssueKeysSnapshot(IssueQueryParams issueQueryParams) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      Optional<Long> changedSinceDate = ofNullable(issueQueryParams.getChangedSince());

      if (changedSinceDate.isPresent()) {
        return dbClient.issueDao().selectIssueKeysByComponentUuidAndChangedSinceDate(dbSession, issueQueryParams.getBranchUuid(),
          changedSinceDate.get(), issueQueryParams.getRuleRepositories(), TaintChecker.getTaintRepositories(),
          issueQueryParams.getLanguages(), issueQueryParams.isResolvedOnly());
      }

      return dbClient.issueDao().selectIssueKeysByComponentUuid(dbSession, issueQueryParams.getBranchUuid(),
        issueQueryParams.getRuleRepositories(), TaintChecker.getTaintRepositories(),
        issueQueryParams.getLanguages(), issueQueryParams.isResolvedOnly(), true);
    }
  }

  @Override
  protected IssueQueryParams initializeQueryParams(BranchDto branchDto, @Nullable List<String> languages,
    @Nullable List<String> ruleRepositories, boolean resolvedOnly, @Nullable Long changedSince) {
    return new IssueQueryParams(branchDto.getUuid(), languages, ruleRepositories, TaintChecker.getTaintRepositories(), resolvedOnly, changedSince);
  }


  @Override
  protected void validateRuleRepositories(List<String> ruleRepositories) {
    checkArgument(ruleRepositories
      .stream()
      .filter(TaintChecker.getTaintRepositories()::contains)
      .count() == 0, "Incorrect rule repositories list: it should only include repositories that define Issues, and no Taint Vulnerabilities");

  }
}
