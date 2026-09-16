import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { FilesModule } from '../files/files.module';
import { AbilitiesController } from './abilities.controller';
import { AbilitiesService } from './abilities.service';

/**
 * Technique Creator. PrismaService est global ; on importe AuthModule (guards)
 * et FilesModule (SFTP write + enfilage reload, FilesService exporté).
 */
@Module({
  imports: [AuthModule, FilesModule],
  controllers: [AbilitiesController],
  providers: [AbilitiesService],
})
export class AbilitiesModule {}
