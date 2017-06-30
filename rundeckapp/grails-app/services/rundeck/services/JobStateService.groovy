/*
 * Copyright 2016 SimplifyOps, Inc. (http://simplifyops.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rundeck.services

import com.dtolabs.rundeck.core.authorization.AuthContext
import com.dtolabs.rundeck.core.dispatcher.ExecutionState
import com.dtolabs.rundeck.core.execution.ExecutionNotFound
import com.dtolabs.rundeck.core.execution.ExecutionReference
import com.dtolabs.rundeck.core.jobs.JobNotFound
import com.dtolabs.rundeck.core.jobs.JobReference
import com.dtolabs.rundeck.core.jobs.JobService
import com.dtolabs.rundeck.core.jobs.JobState
import com.dtolabs.rundeck.server.authorization.AuthConstants
import grails.transaction.Transactional
import org.rundeck.util.Sizes
import rundeck.Execution
import rundeck.ScheduledExecution
import rundeck.services.execution.ExecutionReferenceImpl
import rundeck.services.jobs.AuthorizingJobService
import rundeck.services.jobs.ResolvedAuthJobService

import java.util.concurrent.TimeUnit

@Transactional
class JobStateService implements AuthorizingJobService {
    def frameworkService

    @Override
    JobReference jobForID(AuthContext auth, String uuid, String project) throws JobNotFound {
        def job = ScheduledExecution.getByIdOrUUID(uuid)
        if (null == job || job.project != project) {
            throw new JobNotFound("Not found", uuid, project)
        }
        if (!frameworkService.authorizeProjectJobAll(auth, job, [AuthConstants.ACTION_READ], job.project)) {
            throw new JobNotFound("Not found", uuid, project)
        }
        return new JobReferenceImpl(id: job.extid, jobName: job.jobName, groupPath: job.groupPath, project: job.project)
    }

    @Override
    JobReference jobForName(AuthContext auth, String name, String project) throws JobNotFound {
        def group = null
        if (name.lastIndexOf('/') >= 0 && name.lastIndexOf('/') < name.size() - 1) {
            group = name.substring(0, name.lastIndexOf('/'))
            name = name.substring(name.lastIndexOf('/') + 1)
        }
        return jobForName(auth, group, name, project)
    }

    @Override
    JobReference jobForName(AuthContext auth, String group, String name, String project) throws JobNotFound {
        def job = ScheduledExecution.findByProjectAndJobNameAndGroupPath(project, name, group)
        if (null == job) {
            throw new JobNotFound("Not found", name, group, project)
        }
        if (!frameworkService.authorizeProjectJobAll(auth, job, [AuthConstants.ACTION_READ], job.project)) {
            throw new JobNotFound("Not found", name, group, project)
        }
        return new JobReferenceImpl(id: job.extid, jobName: job.jobName, groupPath: job.groupPath, project: job.project)
    }

    @Override
    JobState getJobState(AuthContext auth, JobReference jobReference) throws JobNotFound {
        def job = ScheduledExecution.getByIdOrUUID(jobReference.id)
        if (null == job) {
            throw new JobNotFound("Not found", jobReference.id, jobReference.project)
        }
        if (!frameworkService.authorizeProjectJobAll(auth, job, [AuthConstants.ACTION_READ], job.project)) {
            throw new JobNotFound("Not found", jobReference.id, jobReference.project)
        }

        List<Execution> running = Execution.findAllByScheduledExecutionAndDateCompletedIsNull(job)
        List<Execution> lastExec = Execution.findAllByScheduledExecutionAndDateCompletedIsNotNull(
                job,
                [order: 'desc', sort: 'dateCompleted', max: 1]
        )
        def previousState = null
        def previousCustom = null
        if (lastExec.size() == 1) {
            previousState = ExecutionState.valueOf(lastExec[0].executionState.replaceAll('[-]', '_'))
            previousCustom=lastExec[0].customStatusString
        }

        def runningIds = new HashSet<String>(running.collect { it.id.toString() })
        new JobStateImpl(
                running: running.size() > 0,
                runningExecutionIds: runningIds,
                previousExecutionState: previousState,
                previousExecutionStatusString: previousCustom
        )
    }
    /**
     * @param auth
     * @return a JobService which uses the auth context for authorization
     */
    JobService jobServiceWithAuthContext(AuthContext auth) {
        return new ResolvedAuthJobService(authContext: auth, authJobService: this)
    }

    @Override
    List<ExecutionReference> searchExecutions(AuthContext auth, String state, String project, String jobUuid,
                                              String excludeJobUuid, String since){

        def executions = Execution.findAllByProjectAndStatus(project,state)
        if(jobUuid){
            executions = executions.findAll{it.scheduledExecution.extid==jobUuid}
        }
        if(excludeJobUuid){
            executions = executions.findAll{it.scheduledExecution.extid!=excludeJobUuid}
        }
        if(since){
            long timeAgo = Sizes.parseTimeDuration(since,TimeUnit.MILLISECONDS)
            Date sinceDt = new Date()
            sinceDt.setTime(sinceDt.getTime()-timeAgo)
            executions = executions.findAll{it.dateStarted.time>sinceDt.time}
        }

        ArrayList<ExecutionReference> list = new ArrayList<>()
        executions.each { exec ->
            ScheduledExecution job = exec.scheduledExecution
            JobReferenceImpl jobRef = new JobReferenceImpl(id: job.extid, jobName: job.jobName,
                    groupPath: job.groupPath, project: job.project)
            ExecutionReferenceImpl execRef = new ExecutionReferenceImpl(id:exec.id, options: exec.argString,
                    filter: exec.filter, job: jobRef, dateStarted: exec.dateStarted, status: exec.status)
            list.add(execRef)
        }
        return list
    }


    @Override
    ExecutionReference executionForId(AuthContext auth, String id, String project) throws ExecutionNotFound{
        long exid = Long.valueOf(id)
        def exec = Execution.findByIdAndProject(exid,project)
        if(!exec){
            throw new ExecutionNotFound("Not found", id, project)
        }
        ScheduledExecution job = exec.scheduledExecution
        JobReferenceImpl jobRef = new JobReferenceImpl(id: job.extid, jobName: job.jobName, groupPath: job.groupPath,
                project: job.project)
        new ExecutionReferenceImpl(id:exec.id, options: exec.argString, filter: exec.filter, job: jobRef,
                dateStarted: exec.dateStarted, status: exec.status)

    }
}
