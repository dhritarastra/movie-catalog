package com.moviecatalog.catalog.person

import com.moviecatalog.catalog.common.RequestArguments
import com.moviecatalog.catalog.common.complete
import com.moviecatalog.catalog.common.toTimestamp
import com.moviecatalog.catalog.v1.CreatePersonRequest
import com.moviecatalog.catalog.v1.DeletePersonRequest
import com.moviecatalog.catalog.v1.DeletePersonResponse
import com.moviecatalog.catalog.v1.GetPersonRequest
import com.moviecatalog.catalog.v1.ListPeopleRequest
import com.moviecatalog.catalog.v1.ListPeopleResponse
import com.moviecatalog.catalog.v1.PersonFields
import com.moviecatalog.catalog.v1.PersonServiceGrpc
import com.moviecatalog.catalog.v1.UpdatePersonRequest
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import com.moviecatalog.catalog.v1.Person as PersonProto

/**
 * gRPC endpoint for people, shaped like MovieGrpcService. GetPeopleByIds still returns
 * UNIMPLEMENTED: it arrives with credits, which are its only caller.
 */
@Service
class PersonGrpcService(private val people: PersonService) : PersonServiceGrpc.PersonServiceImplBase() {
    override fun createPerson(request: CreatePersonRequest, responseObserver: StreamObserver<PersonProto>) =
        responseObserver.complete(people.create(request.fields.toInput()).toProto())

    override fun getPerson(request: GetPersonRequest, responseObserver: StreamObserver<PersonProto>) =
        responseObserver.complete(people.get(RequestArguments.uuid("id", request.id)).toProto())

    override fun listPeople(request: ListPeopleRequest, responseObserver: StreamObserver<ListPeopleResponse>) {
        val page = people.list(request.page.offset, request.page.limit)
        responseObserver.complete(
            ListPeopleResponse.newBuilder()
                .addAllPeople(page.content.map { it.toProto() })
                .setTotalCount(page.totalElements.toInt())
                .build(),
        )
    }

    override fun updatePerson(request: UpdatePersonRequest, responseObserver: StreamObserver<PersonProto>) =
        responseObserver.complete(
            people.update(
                id = RequestArguments.uuid("id", request.id),
                expectedVersion = RequestArguments.expectedVersion(request.expectedVersion),
                input = request.fields.toInput(),
            ).toProto(),
        )

    override fun deletePerson(request: DeletePersonRequest, responseObserver: StreamObserver<DeletePersonResponse>) {
        val id = RequestArguments.uuid("id", request.id)
        people.delete(id, RequestArguments.expectedVersion(request.expectedVersion))
        responseObserver.complete(DeletePersonResponse.newBuilder().setId(id.toString()).build())
    }
}

private fun PersonFields.toInput() =
    PersonInput.normalised(
        name = name,
        bio = if (hasBio()) bio else null,
        birthDate = if (hasBirthDate()) birthDate else null,
    )

// Receivers are named explicitly: inside the builder's apply, a bare `bio` would be the builder's own field.
private fun Person.toProto(): PersonProto =
    PersonProto.newBuilder()
        .setId(requireNotNull(id) { "a saved person has an id" }.toString())
        .setName(name)
        .setVersion(version)
        .setCreatedAt(createdAt.toTimestamp())
        .setUpdatedAt(updatedAt.toTimestamp())
        .apply {
            this@toProto.bio?.let { setBio(it) }
            this@toProto.birthDate?.let { setBirthDate(it.toString()) }
        }
        .build()
