package io.github.ameralkhorasani.outpost.di

import android.content.Context
import androidx.room.Room
import io.github.ameralkhorasani.outpost.data.db.AppDatabase
import io.github.ameralkhorasani.outpost.data.db.PortForwardDao
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.security.SecureKeyManager
import io.github.ameralkhorasani.outpost.ssh.docker.DockerRepository
import io.github.ameralkhorasani.outpost.ssh.SshRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "outpost_db"
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideServerDao(database: AppDatabase): ServerDao {
        return database.serverDao()
    }

    @Provides
    fun providePortForwardDao(database: AppDatabase): PortForwardDao {
        return database.portForwardDao()
    }

    @Provides
    @Singleton
    fun provideSecureKeyManager(@ApplicationContext context: Context): SecureKeyManager {
        return SecureKeyManager(context)
    }

    @Provides
    @Singleton
    fun provideSshRepository(secureKeyManager: SecureKeyManager): SshRepository {
        return SshRepository(secureKeyManager)
    }

    @Provides
    @Singleton
    fun provideDockerRepository(sshRepository: SshRepository): DockerRepository {
        return DockerRepository(sshRepository)
    }
}
