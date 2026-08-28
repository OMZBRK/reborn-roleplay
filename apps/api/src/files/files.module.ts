import { Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { CommandBridgeGuard } from './command-bridge.guard';
import { CommandsController } from './commands.controller';
import { FilesController } from './files.controller';
import { FilesService } from './files.service';

@Module({
  imports: [AuthModule, AuditModule],
  controllers: [FilesController, CommandsController],
  providers: [FilesService, CommandBridgeGuard],
})
export class FilesModule {}
