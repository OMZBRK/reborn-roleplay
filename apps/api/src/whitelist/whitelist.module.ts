import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { WhitelistController } from './whitelist.controller';
import { WhitelistService } from './whitelist.service';
import { WhitelistMessagesService } from './whitelist-messages.service';

@Module({
  imports: [AuthModule],
  controllers: [WhitelistController],
  providers: [WhitelistService, WhitelistMessagesService],
  // Exporté pour que StaffModule puisse appeler postStaffMessage quand le
  // bot relaie un message staff via /staff/whitelist/:id/messages.
  exports: [WhitelistMessagesService],
})
export class WhitelistModule {}
